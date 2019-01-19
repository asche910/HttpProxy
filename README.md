# HttpProxy
JAVA实现的IP代理池，支持HTTP与HTTPS两种方式


```
设置Ip代理很多时候都会有用到，尤其是在写爬虫相关项目的时候。虽然自己目前没有接触这种需求，但由于最近比较闲，就写着当作练习吧
```

## 爬取代理IP

### 爬取
关于爬取代理IP，国内首先想到的网站当然是 [西刺代理](https://www.xicidaili.com/) 。首先写个爬虫获取该网站内的Ip吧。

先对 [国内Http代理](https://www.xicidaili.com/wt/) 标签页面进行爬取，解析页面使用的[Jsoup](https://jsoup.org/) ，这里大概代码如下

```java
 private List<IPBean> crawl(String api, int index){
        String html = HttpUtils.getResponseContent(api + index);
        System.out.println(html);

        Document document = Jsoup.parse(html);
        Elements eles = document.selectFirst("table").select("tr");

        for (int i = 0; i < eles.size(); i++){
            if (i == 0) continue;
            Element ele = eles.get(i);
            String ip = ele.children().get(1).text();
            int port = Integer.parseInt(ele.children().get(2).text().trim());
            String typeStr = ele.children().get(5).text().trim();

            int type;
            if ("HTTP".equalsIgnoreCase(typeStr))
                type = IPBean.TYPE_HTTP;
            else
                type = IPBean.TYPE_HTTPS;

            IPBean ipBean = new IPBean(ip, port, type);
            ipList.add(ipBean);
        }
        return ipList;
    }
```
对某些不明白的变量，可以参考我[Github](https://github.com/asche910/HttpProxy)
其中关键的就是css选择器语法，这里需要注意的是不要乱加空格，不然会导致找不到出现空指针。
css选择器语法具体[参考这里](https://jsoup.org/cookbook/extracting-data/selector-syntax) ， 这里就不讲解了。

爬取的信息包括 ip地址、端口号、和代理类型(http或https), 这三个信息我放在IPBean这个类里面。

### 过滤

上面爬取完成后，还要进一步过滤，筛选掉不能使用的。

筛选大概原理就是先设置上代理，然后请求某个网页，若成功则代表此代理ip有效。
其中请求成功的标志我们可以直接获取请求的返回码，若为200即成功。

```java
    /**
     * 检测代理ip是否有效
     *
     * @param ipBean
     * @return
     */
    public static boolean isValid(IPBean ipBean) {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ipBean.getIp(), ipBean.getPort()));
        try {
            URLConnection httpCon = new URL("https://www.baidu.com/").openConnection(proxy);
            httpCon.setConnectTimeout(5000);
            httpCon.setReadTimeout(5000);
            int code = ((HttpURLConnection) httpCon).getResponseCode();
            System.out.println(code);
            return code == 200;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
```
注意这里要设置两个超时，连接超时和读取超时。连接超时还好，它默认只是有点长；然而读取超时如果不设置，它好像就会一直阻塞着。
时间设置为5s就够了，毕竟如果ip有效的话，会很快就请求成功的。这样过滤后，就得到有效的代理ip了

## 设置代理

### 单次代理
单次代理表示只在这一次连接中有效，即每次都需要代理。

http方式的代理非常简单，在URL对象的openConnection方法中加上个Proxy对象即可
```java

 Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ipBean.getIp(), ipBean.getPort()));

 connection = (HttpsURLConnection) new URL(url).openConnection(proxy);

```

https 稍微复杂点了，中间加上了ssl协议

```java

    /**
     * @param url
     * @param headerMap 请求头部
     * @param ipBean
     * @return
     * @throws Exception
     */
    public static String getResponseContent(String url, Map<String, List<String>> headerMap, IPBean ipBean) throws Exception {
        HttpsURLConnection connection = null;

        // 设置代理
        if (ipBean != null) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ipBean.getIp(), ipBean.getPort()));

            connection = (HttpsURLConnection) new URL(url).openConnection(proxy);

            if (ipBean.getType() == IPBean.TYPE_HTTPS) {
                SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, new TrustManager[]{new TrustAnyTrustManager()}, new java.security.SecureRandom());
                connection.setSSLSocketFactory(sslContext.getSocketFactory());
                connection.setHostnameVerifier(new TrustAnyHostnameVerifier());
            }
        }

        if (connection == null)
            connection = (HttpsURLConnection) new URL(url).openConnection();

        // 添加请求头部
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.81 Safari/537.36");
        if (headerMap != null) {
            Iterator<Map.Entry<String, List<String>>> iterator = headerMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, List<String>> entry = iterator.next();
                List<String> values = entry.getValue();
                for (String value : values)
                    connection.setRequestProperty(entry.getKey(), value);
            }
        }

        InputStream inputStream = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);
        }
        reader.close();
        inputStream.close();
        return stringBuilder.toString();
    }


    private static class TrustAnyTrustManager implements X509TrustManager {

        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{};
        }
    }

    private static class TrustAnyHostnameVerifier implements HostnameVerifier {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }

```

这里https方法参考了 [这篇博客](https://blog.csdn.net/sbc1232123321/article/details/79334130)


### 全局代理
直接上代码，就几行代码
```java
package util;

import other.IPBean;

/**
 * @author Asche
 * @github: https://github.com/asche910
 * @date 2019年1月19日
 */
public class ProxyUtils {

    /**
     * 设置全局代理
     * @param ipBean
     */
    public static void setGlobalProxy(IPBean ipBean){
        System.setProperty("proxyPort", String.valueOf(ipBean.getPort()));
        System.setProperty("proxyHost", ipBean.getIp());
        System.setProperty("proxySet", "true");
    }

}

```
需要注意一点就是全局只是在该java项目中生效，它不会更改系统中的代理。

### 检测
设置完代理后，也可以用另外一种方法来判断是否代理成功，即直接获取当前ip地址。
这里我使用的是 https://www.ipip.net/ip.html  这个网站，请求获取html后再解析得到自己的当前ip

``` java

 private static final String MY_IP_API = "https://www.ipip.net/ip.html";

    // 获取当前ip地址，判断是否代理成功
    public static String getMyIp() {
        try {
            String html = HttpUtils.getResponseContent(MY_IP_API);

            Document doc = Jsoup.parse(html);
            Element element = doc.selectFirst("div.tableNormal");

            Element ele = element.selectFirst("table").select("td").get(1);

            String ip = element.selectFirst("a").text();

            // System.out.println(ip);
            return ip;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
``` 


## 优化

emmm 优化些啥呢？？？

### 速度

爬取ip时就几个网页，优化估计效果不大。而真正耗时的是检测ip是否有效，因此这里采用多线程，对每个ip的检测请求使用一个线程，最后副线程全部结束后再统计出有多少有效ip。然而问题又来了，怎么判断所有副线程全部结束了呢？？？ 脑中立刻想到的是join方法，然而仔细想想，才发现这样并不可取。最佳方法应该是设置一个计数器，每个线程结束后计数器加一，然后在主线程循环判断计数器的值是否与线程总数相等即可。由于涉及到并发，需要给某些方法加上锁。这里我代码中实现了，可以参考[github](https://github.com/asche910/HttpProxy)


![](https://img2018.cnblogs.com/blog/1470456/201901/1470456-20190119144416843-1338561809.png)


### 持久化

emmm 由于目前只是练练手，并没有这样的需求，比较懒， ~(￣▽￣)~*
所以这个需求暂时放放吧，以后有时间再写

