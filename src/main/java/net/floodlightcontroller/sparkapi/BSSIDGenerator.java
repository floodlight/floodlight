package net.floodlightcontroller.sparkapi;

import java.util.Arrays;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class BSSIDGenerator {

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static String insertChars(String s, char c){
        for (int i = 2; i < s.length(); i+=3)
            s = s.substring(0, i) + c + s.substring(i, s.length());
        return s;
    }

    public static String getUniqueBSSID(String m) {

        //will be needed later for byte overwrite
        String tmp_mac;
        String e_mac = "ee:ee:ee:ee:ee:ee";
        tmp_mac = e_mac; // ee:ee:ee:ee:ee:ee MAC
        byte[] tmp_byte = tmp_mac.getBytes(); //6 bytes

        //dumb init
        String BSSID = null;

        //dat dvojbodky z MAC adresy do prec
        m = m.replace(":", "");

        //convert from MacAddress to bytes
        byte[] buffer = m.getBytes();

        //create SHA1 fingerprint & convert to byte array & back to MacAddress
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(buffer);
            byte[] digest = Arrays.copyOfRange(md.digest(),0,6); //take the first 6 bytes from the fingerprint

            //overwrite specific bytes in byte array with byte value of 'e' ...hork's wish
            digest[0] = tmp_byte[0];
            digest[1] = tmp_byte[1];

            //BSSID = digest.toString();
            BSSID = bytesToHex(digest);

        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return insertChars(BSSID, ':');
    }
}
