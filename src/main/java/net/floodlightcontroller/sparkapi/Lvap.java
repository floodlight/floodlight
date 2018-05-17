package net.floodlightcontroller.sparkapi;


public class Lvap {

    private String mac;
    private String ip;
    private String bssid;
    private String ssid;
    private String wtp;

    public Lvap(String mac, String ip, String bssid, String ssid, String wtp) {
        this.mac = mac;
        this.ip = ip;
        this.bssid = bssid;
        this.ssid = ssid;
        this.wtp = wtp;
    }

    public String toJson(){
        return new com.google.gson.Gson().toJson(this);
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getBssid() {
        return bssid;
    }

    public void setBssid(String bssid) {
        this.bssid = bssid;
    }

    public String getSsid() {
        return ssid;
    }

    public void setSsid(String ssid) {
        this.ssid = ssid;
    }

    public String getWtp() {
        return wtp;
    }

    public void setWtp(String wtp) {
        this.wtp = wtp;
    }
}
