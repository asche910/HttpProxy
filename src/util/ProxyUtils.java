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
