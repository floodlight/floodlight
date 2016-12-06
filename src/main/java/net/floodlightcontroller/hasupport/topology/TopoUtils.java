package net.floodlightcontroller.hasupport.topology;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TopoUtils {
	protected static Logger logger = LoggerFactory.getLogger(TopoUtils.class);
	private final String[] lowfields = new String[]{"src", "srcPort", "dst","dstPort","type"};
	
	public List<String> parseChunk(String chunk){
		
		ObjectMapper mapper = new ObjectMapper();
		Map<String,Object> newJson = new HashMap<String,Object>();
		List<String> jsonInString = new LinkedList<String>();
		
		String op          = new String();
		String src         = new String();
		String srcPort     = new String();
		String dst         = new String();
		String dstPort     = new String();
		String latency     = new String();
		String type        = new String();
		
		
		if(! chunk.startsWith("LDUpdate [") ){
			return jsonInString;
		}
		
		try {
		while(!chunk.equals("]]")){
			
			// pre
			if(chunk.startsWith("LDUpdate [")){
				chunk = chunk.substring(10, chunk.length());
			}
			logger.debug("\n[Assemble Topo Update] Chunk pre: {}", new Object[] {chunk});
			
			//process keywords	
			
			// field: operation
			if(chunk.startsWith("operation=")){
				chunk = chunk.substring(10,chunk.length());
				op = chunk.split(",|]")[0];
				logger.debug("[Assemble Topo Update] Operation=: {}", new Object[]{op});
				chunk = chunk.substring(op.length(), chunk.length());
			}
			
			if(chunk.startsWith(", ")){
				chunk = chunk.substring(2, chunk.length());
			}
			
			logger.debug("\n[Assemble Topo Update] Chunk keywords: {}", new Object[] {chunk});
			
			// field: src
			if(chunk.startsWith("src=")){
				chunk = chunk.substring(4,chunk.length());
			    src = chunk.split(",|]")[0];
				logger.debug("[Assemble Topo Update] Src=: {}", new Object[]{src});
				chunk = chunk.substring(src.length(), chunk.length());
			}
			
			if(chunk.startsWith(", ")){
				chunk = chunk.substring(2, chunk.length());
			}
			
			logger.debug("\n[Assemble Topo Update] Chunk keywords: {}", new Object[] {chunk});
			
			// field: srcPort
			if(chunk.startsWith("srcPort=")){
				chunk = chunk.substring(8,chunk.length());
				srcPort = chunk.split(",|]")[0];
				logger.debug("[Assemble Topo Update] SrcPort=: {}", new Object[]{srcPort});
				chunk = chunk.substring(srcPort.length(), chunk.length());
			}
			
			if(chunk.startsWith(", ")){
				chunk = chunk.substring(2, chunk.length());
			}
			
			logger.debug("\n[Assemble Topo Update] Chunk keywords: {}", new Object[] {chunk});
			
			// field: dst
			if(chunk.startsWith("dst=")){
				chunk = chunk.substring(4,chunk.length());
				dst = chunk.split(",|]")[0];
				logger.debug("[Assemble Topo Update] Dst=: {}", new Object[]{dst});
				chunk = chunk.substring(dst.length(), chunk.length());
			}
			
			if(chunk.startsWith(", ")){
				chunk = chunk.substring(2, chunk.length());
			}
			
			logger.debug("\n[Assemble Topo Update] Chunk keywords: {}", new Object[] {chunk});
			
			// field: dstPort
			if(chunk.startsWith("dstPort=")){
				chunk = chunk.substring(8,chunk.length());
				dstPort = chunk.split(",|]")[0];
				logger.debug("[Assemble Topo Update] DstPort=: {}", new Object[]{dstPort});
				chunk = chunk.substring(dstPort.length(), chunk.length());
			}
			
			if(chunk.startsWith(", ")){
				chunk = chunk.substring(2, chunk.length());
			}
			
			logger.debug("\n[Assemble Topo Update] Chunk keywords: {}", new Object[] {chunk});
			
			// field: latency
			if(chunk.startsWith("latency=")){
				chunk = chunk.substring(8,chunk.length());
				latency = chunk.split(",|]")[0];
				logger.debug("[Assemble Topo Update] Latency=: {}", new Object[]{latency});
				chunk = chunk.substring(latency.length(), chunk.length());
			}
			
			if(chunk.startsWith(", ")){
				chunk = chunk.substring(2, chunk.length());
			}
			
			logger.debug("\n[Assemble Topo Update] Chunk keywords: {}", new Object[] {chunk});
			
			// field: type
			if(chunk.startsWith("type=")){
				chunk = chunk.substring(5,chunk.length());
				type = chunk.split(",|]")[0];
				logger.debug("[Assemble Topo Update] Type=: {}", new Object[]{type});
				chunk = chunk.substring(type.length(), chunk.length());
			}
			
			if(chunk.startsWith(", ")){
				chunk = chunk.substring(2, chunk.length());
			}
			
			logger.debug("\n[Assemble Topo Update] Chunk keywords: {}", new Object[] {chunk});
			
			//post
			if(chunk.startsWith("], ")){
				chunk = chunk.substring(3, chunk.length());
			}
			logger.debug("\n[Assemble Topo Update] Chunk post: {}", new Object[] {chunk});
			
			//TODO: Put it in a JSON.
			if(! op.isEmpty() ){
				newJson.put("operation", op);
			}
			if(! src.isEmpty() ){
				newJson.put("src", src);
			}
			if(! srcPort.isEmpty() ){
				newJson.put("srcPort", srcPort);
			}
			if(! dst.isEmpty() ){
				newJson.put("dst", dst);
			}
			if(! dstPort.isEmpty() ){
				newJson.put("dstPort", dstPort);
			}
			if(! latency.isEmpty() ){
				newJson.put("latency", latency);
			}
			if(! type.isEmpty() ){
				newJson.put("type", type);
			}
			
			try{
				jsonInString.add(mapper.writeValueAsString(newJson));
			} catch (JsonProcessingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		} 
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return jsonInString;
	}
	
	public String calculateMD5Hash(String value){
		String md5 = new String();
		try {
			MessageDigest m = MessageDigest.getInstance("MD5");
			m.reset();
			m.update(value.getBytes());
			byte[] digest = m.digest();
			BigInteger bigInt = new BigInteger(1,digest);
			md5 = bigInt.toString(16);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return md5;	
	}
	
	public String getCMD5Hash(String update, Map<String, String> newUpdateMap) {				
		ArrayList<String> cmd5fields = new ArrayList<String>(); 
		String cmd5 = new String();
		//check map for low freq updates
		for (String lf: lowfields){
			if (newUpdateMap.containsKey(lf)){
				cmd5fields.add(newUpdateMap.get(lf));
			}
		}
		
		//cmd5fields will contain all low freq field values; take md5 hash of all values together.
		StringBuilder md5valuesb = new StringBuilder();
		for (String t: cmd5fields){
			md5valuesb.append(t);
		}
		String md5values = new String();
		md5values = md5valuesb.toString();
		
		// updateMap.put("cmd5",hash(md5values))
		// hash(...) -> means that take md5 hash of "..." 
		
		try {
			TopoUtils myCMD5 = new TopoUtils();
			cmd5 = myCMD5.calculateMD5Hash(md5values);
			logger.debug("[cmd5Hash] The MD5: {} The Value {}", new Object [] {cmd5,md5values.toString()}); //use md5values instead of updates.	
		} 
		catch (Exception e){
			logger.debug("[cmd5Hash] Exception: enqueueFwd!");
			e.printStackTrace();
		}
		return cmd5;
	}
	
	public String appendUpdate(String oldUpdate, String newUpdate) {
		
		StringBuilder updated = new StringBuilder();
		
		updated.append(oldUpdate);
		updated.append(", ");
		updated.append(newUpdate);
	
		return updated.toString();
	
	}

}
