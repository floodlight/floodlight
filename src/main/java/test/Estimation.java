package test;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import net.floodlightcontroller.firewall.FirewallRule;
import net.floodlightcontroller.firewall.WildcardsPair;
import net.floodlightcontroller.packet.Ethernet;

public class Estimation {

	public static void main(String[] args) {
		mytest test = new mytest();
		// TODO Auto-generated method stub
		String rulePath="C:\\Users\\niuni\\workspace\\floodlight\\data\\acl3_512k_rules.txt";
		String ethPath="C:\\Users\\niuni\\workspace\\floodlight\\data\\acl3_512k_rules.txt_trace";
		List<FirewallRule> rules =  test.GenerateRule(rulePath);
        List<Ethernet> eths =  test.GenerateEthernet(ethPath); 
        WildcardsPair wildcards=new WildcardsPair();
        Iterator<Ethernet> iterEth=eths.iterator();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
        test.printWrite(df.format(new Date()));// new Date()为获取当前系统时间
        test.printWrite("go!!");
        double start  = System.currentTimeMillis() ; 
        synchronized(iterEth){
       // while(iterEth.hasNext()){
        for(Ethernet eth:eths){
/*        		Iterator<FirewallRule> iterRule = rules.iterator();
        		synchronized (iterRule) {
        		FirewallRule rule = null;
        		while (iterRule.hasNext()) {
        			rule = iterRule.next();
        			if (rule.matchesFlow(1L, (short)1, eth, wildcards) == true) {
        				break;
        			}				
        		}        				
        		}*/
        	for(FirewallRule rule:rules){
        		if (rule.matchesFlow(1L, (short)1, eth, wildcards) == true) {
    				break;
        	}
        }
        }
        double end  = System.currentTimeMillis() ; 
        double time = end - start;
        test.printWrite("=>Time of Entry_for_packet:"+time);
        test.printWrite("The size of table is : "+rules.size());
        test.printWrite("The number of packets is:"+eths.size());
        test.printWrite("The speed is: "+(int)(1000*eths.size()/time)+" packet/s");
        test.printWrite("The average speed is: "+test.getAverage()+" packet/s");
        test.printWrite("end.\n"+df.format(new Date())+"\n**************************************");
	}
}}
