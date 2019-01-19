import util.IPUtils;

public class Test {
    public static void main(String[] args) {
        System.out.println("Test Start: ");
        String ip = "119.101.118.61";

        System.out.println(IPUtils.getMyIp());

        System.setProperty("proxyPort", "9999");
        System.setProperty("proxyHost", ip);
        System.setProperty("proxySet", "true");

        System.out.println(IPUtils.getMyIp());
    }
}
