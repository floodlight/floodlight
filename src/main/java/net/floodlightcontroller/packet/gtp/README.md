General Packet Radio Service (GPRS) and GPRS Tunnelling Protocol (GTP) implementation
====================================

What is this?
-------------
This is java implementation of the GTPv1 and GTPv2 packets linked to the Floodlight controller. The document used as reference for the GTP packets header was 3GPP TS 29.060 V12.9.0 (2015-06) (can be found [here](https://arib.or.jp/english/html/overview/doc/STD-T63v11_30/5_Appendix/Rel12/29/29060-c90.pdf)). According to the previously cited document, the GTP tunnelling protocol is the protocol between GPRS support nodes (GSNs) in the UMTS/GPRS backbone network and it includes both the GTP control plane (GTP-C) and data transfer (GTP-U) procedures.

Should I use it?
------------
If you intent on handling packets from GTP tunnels for any special reason and believe that this will help you in any way please be got for it. It should make your life easier when handling this types of packages in Floodlight (for instance, when creating a flow mod for GTP traffic).

Why does this exist?
-------------
This was used to evaluate feasibility of transparent cache in LTE mobile backhaul. The idea was to evalute the overhead of handling this kinds of packages and performing TCP splicing to transparently redirect client requests. Paper with the results can be found [here](https://people.kth.se/~gyuri/Pub/RodriguezDG-SWFan2016-SDNCaching.pdf).


