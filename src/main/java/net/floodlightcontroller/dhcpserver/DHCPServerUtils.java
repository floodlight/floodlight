package net.floodlightcontroller.dhcpserver;

import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.List;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 1/3/18
 */
public class DHCPServerUtils {
    /* Convert int to byte[] */
    public static byte[] intToBytes(int integer) {
        byte[] bytes = new byte[4];
        bytes[3] = (byte) (integer >> 24);
        bytes[2] = (byte) (integer >> 16);
        bytes[1] = (byte) (integer >> 8);
        bytes[0] = (byte) (integer);
        return bytes;
    }

    /* Convert int to byte[] with one byte */
    public static byte[] intToBytesSizeOne(int integer) {
        byte[] bytes = new byte[1];
        bytes[0] = (byte) (integer);
        return bytes;
    }

    /* Convert List<IPv4Address> to byte[] */
    public static byte[] IPv4ListToByteArr(List<IPv4Address> IPv4List){
        byte[] byteArray = new byte[IPv4List.size() * 4]; 	// IPv4Address is 4 bytes
        for(int i = 0; i < IPv4List.size(); ++i){
            byte[] IPv4ByteArr = new byte[4];
            int index = i * 4;
            IPv4ByteArr = IPv4List.get(i).getBytes();
            for(int j = 0; j < IPv4ByteArr.length; ++j){
                byteArray[index+j] = IPv4ByteArr[j];
            }
        }
        return byteArray;
    }
}
