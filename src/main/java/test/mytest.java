package test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.floodlightcontroller.firewall.FirewallRule;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;

public class mytest {
	 String resultPath="C:\\Users\\niuni\\workspace\\floodlight\\data\\result";
	public  List<FirewallRule> GenerateRule(String rulePath){
		List<FirewallRule> rules = new ArrayList<FirewallRule>();

		String line;
		String [] strRule;
		int i=10000;
		try {
			BufferedReader reader =new BufferedReader(new FileReader(rulePath));
			while((line=reader.readLine())!=null){
				FirewallRule rule = new FirewallRule();
				strRule=line.split("\\s+");
				int flag0=strRule[0].indexOf('/');
				String srcIP=strRule[0].substring(0, flag0);
				int srcMask = Integer.parseInt(strRule[0].substring(flag0+1));
				int flag1=strRule[1].indexOf('/');
				String dstIP=strRule[1].substring(0, flag1);
				int dstMask = Integer.parseInt(strRule[1].substring(flag1+1));
				short port = (short) (Integer.parseInt(strRule[5])-32768);
				rule.dpid=1L;
				rule.in_port=1;
				rule.dl_dst=1L;
				rule.dl_src=0L;
				rule.dl_type=Ethernet.TYPE_IPv4;
				rule.nw_dst_maskbits=dstMask;
				rule.nw_dst_prefix=IPv4.toIPv4Address(dstIP);
				rule.nw_proto=IPv4.PROTOCOL_TCP;
				rule.nw_src_maskbits=srcMask;
				rule.nw_src_prefix=IPv4.toIPv4Address(srcIP);
				rule.tp_dst=port;
				rule.tp_src=1;
				rule.priority=--i;
				rule.wildcard_dl_type=false;
				rule.wildcard_dl_dst=false;
				rule.wildcard_dl_src=false;
				rule.wildcard_in_port=false;
				rule.wildcard_dpid=false;
				rule.wildcard_nw_src=false;
				rule.wildcard_nw_dst=false;
				rule.wildcard_nw_proto=false;
				rule.wildcard_tp_dst=false;
				rules.add(rule);
			}
			reader.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.out.println(i);
			e.printStackTrace();
		}
		return rules;
	}
	public  List<Ethernet> GenerateEthernet(String ethPath){
		List<Ethernet> ethernets=new ArrayList<Ethernet>();
		String line;
		String [] strEth;
		try {
			BufferedReader reader =new BufferedReader(new FileReader(ethPath));
			while((line = reader.readLine())!=null){
				strEth=line.split("\\s+");
				int srcIP=IPv4.toIPv4Address(iplongToIp(Long.parseLong(strEth[0])));
				int dstIP=IPv4.toIPv4Address(iplongToIp(Long.parseLong(strEth[1])));
				//short srcPort = Short.parseShort(strEth[2]);
				short dstPort = (short) (Integer.parseInt(strEth[3])-32768);
				TCP tcp = new TCP();
				IPv4 ip4=new IPv4();
				Ethernet ethernet = new Ethernet();
				tcp.setDestinationPort(dstPort);
				tcp.setSourcePort((short) 1);
				
				Data data=new Data();
				data.setData("hello".getBytes());
				tcp.setPayload(data);
				ip4.setProtocol(IPv4.PROTOCOL_TCP);
				ip4.setDestinationAddress(dstIP);
				ip4.setSourceAddress(srcIP);
				ip4.setPayload(tcp);
				ethernet.setPayload(ip4);
				ethernet.setEtherType(Ethernet.TYPE_IPv4);
				ethernet.setDestinationMACAddress("00:00:00:00:00:01");
				ethernet.setSourceMACAddress("00:00:00:00:00:00");
				ethernets.add(ethernet);
			}
			reader.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ethernets;
	}
	public  String iplongToIp(long ipaddress) {  
        StringBuffer sb = new StringBuffer("");  
        sb.append(String.valueOf((ipaddress >>> 24)));  
        sb.append(".");  
        sb.append(String.valueOf((ipaddress & 0x00FFFFFF) >>> 16));  
        sb.append(".");  
        sb.append(String.valueOf((ipaddress & 0x0000FFFF) >>> 8));  
        sb.append(".");  
        sb.append(String.valueOf((ipaddress & 0x000000FF)));  
        return sb.toString();  
    } 
	public  void printWrite(String s) {
		FileWriter writer;
		try {
			writer = new FileWriter(resultPath, true);
			writer.write(s+"\n");
			System.out.println(s);
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}
	public  int getAverage(){
		try {
			String s;
			int p=-1,pnumber=0;
			double t=-1,tnumber=0;
			BufferedReader reader =new BufferedReader(new FileReader(resultPath));
			while((s=reader.readLine())!=null){
				p=s.indexOf("of packets is:");
				if(p>=0){
					String[]pp=s.split(":");
					pnumber=pnumber+Integer.parseInt(pp[1]);
				}
				t=s.indexOf("Entry_for_packet:");
				if(t>=0){
					String[] tt=s.split(":");
					tnumber=tnumber+Double.parseDouble(tt[1]);
				}
			}
			reader.close();
			if(tnumber>0)
				return (int) (1000*pnumber/tnumber);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}
}
