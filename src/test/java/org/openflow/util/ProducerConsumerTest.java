package org.openflow.util;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProducerConsumerTest {

	protected static interface _IConsumer {}
	
	protected static interface _IFace1 {
		void method1 (Object anArg);
	}

	protected static interface _IFace2 {
		void method2 (Object anArg);
	}

	protected static interface _IFace3 {}
	
	protected static Class<?> [] _iface1List  = {_IFace1.class};
	protected static Class<?> [] _iface12List = {_IFace1.class, _IFace2.class};
	protected static Class<?> [] _iface23List = {_IFace2.class, _IFace3.class};
	
	protected abstract static class _BaseConsumer implements _IConsumer
	{
		Class<?> [] ifaceList;
		
		_BaseConsumer (Class<?> [] ifaceList)
		{
			this.ifaceList = ifaceList;
		}

		int registeredCount ()
		{
			Set<Class<?>> supportedIfaces = ProducerConsumer.getSingleton ().registerConsumer (this);
			int			  count			  = 0;
			
			for (Class<?> iface: ifaceList)
				if (supportedIfaces.contains (iface))
					count++;
			
			return count;				
		}

		boolean registerConsumer ()
		{
			return registeredCount () == ifaceList.length;
		}

	}

	protected abstract static class _BaseProducer implements IProducer
	{
		Class<?> []				   ifaceList;
		Map<Class<?>, Set<Object>> ifaceMap = new Hashtable<Class<?>, Set<Object>> ();

		protected _BaseProducer (Class<?> [] ifaceList)
		{
			this.ifaceList = ifaceList;
		}

		protected int registerProducer ()
		{
			int count = 0;

			for (Class<?> iface: ifaceList) {
				if (iface.isInterface ()) {
					if (ProducerConsumer.getSingleton ().registerProducer (this, iface))
						count++;
				}
			}
			
			return count;
		}

		protected int deregisterProducer ()
		{
			int count = 0;

			for (Class<?> iface: ifaceList) {
				if (iface.isInterface ()) {
					if (ProducerConsumer.getSingleton ().deregisterProducer (this, iface))
						count ++;
				}
			}
			
			return count;
		}
		
		protected int consumerCount ()
		{
			int count = 0;
		
			for (Class<?> iface: ifaceList) {
				Set<Object> aSet = ifaceMap.get (iface);
				
				if (aSet != null)
					count += aSet.size ();
			}

			return count;
		}
		
		protected abstract int runCount ();

		@Override
		public void registerConsumer (Class<?> iface, Object anObj)
		{
			Set<Object> consumers = ifaceMap.get (iface);
			
			if (consumers == null) {
				consumers = new HashSet<Object> ();
				ifaceMap.put (iface, consumers);
			}

			consumers.add (anObj);
		}

		@Override
		public void deregisterConsumer (Class<?> iface, Object anObj)
		{
			Set<Object> consumers = ifaceMap.get (iface);
			
			if (consumers != null)
				consumers.remove (anObj);
		}
		
	}
	
	protected static class _Consumer1 extends _BaseConsumer implements _IFace1 {

		protected _Consumer1 ()
		{
			this (_iface1List);
		}

		protected _Consumer1 (Class<?>[] ifaceList)
		{
			super (ifaceList);
		}

		@Override
		public void method1 (Object anArg) {}
		
	}

	protected static class _Consumer12 extends _Consumer1 implements _IFace1, _IFace2 {

		protected _Consumer12 ()
		{
			super (_iface12List);
		}

		@Override
		public void method2 (Object anArg) {}

	}

	protected static class _Producer1 extends _BaseProducer {

		protected _Producer1 ()
		{
			super (_iface1List);
		}

		@Override
		protected int runCount ()
		{
			int count = 0;

			for (Class<?> iface: ifaceList) {
				Set<Object> aSet = ifaceMap.get (iface);
				
				if (aSet != null) {
					if (iface == _IFace1.class) {
						for (Object anObj: aSet) {
							((_IFace1) anObj).method1 (anObj);
							count++;
						}
					}
				}
			}

			return count;
		}
		
	}
	
	protected static class _Producer12 extends _BaseProducer {

		protected _Producer12 ()
		{
			super (_iface12List);
		}

		@Override
		protected int runCount ()
		{
			int count = 0;

			for (Class<?> iface: ifaceList) {
				Set<Object> aSet = ifaceMap.get (iface);
				
				if (aSet != null) {
					if		(iface == _IFace1.class) {
						for (Object anObj: aSet) {
							((_IFace1) anObj).method1 (anObj);
							count++;
						}
					}
					else if (iface == _IFace2.class) {
						for (Object anObj: aSet) {
							((_IFace2) anObj).method2 (anObj);
							count++;
						}
					}
				}
			}

			return count;
		}
		
	}
	
	protected static class _Producer23 extends _BaseProducer {

		protected _Producer23 ()
		{
			super (_iface23List);
		}

		@Override
		protected int runCount ()
		{
			int count = 0;

			for (Class<?> iface: ifaceList) {
				Set<Object> aSet = ifaceMap.get (iface);
				
				if (aSet != null) {
					if		(iface == _IFace2.class) {
						for (Object anObj: aSet) {
							((_IFace2) anObj).method2 (anObj);
							count++;
						}
					}
					else if (iface == _IFace3.class) {
						for (Object anObj: aSet) {
							_IFace3 iface3 = (_IFace3) anObj;
							
							if (iface3 != null)
								count++;
						}
					}
				}
			}

			return count;
		}
		
	}
	
	protected List<_BaseProducer> producers = new ArrayList<_BaseProducer> ();
	protected List<_BaseConsumer> consumers = new ArrayList<_BaseConsumer> ();

	protected static final String _REG_P_STR_   = "Registering producers";
	protected static final String _REG_C_STR_   = "Registering consumers";
	protected static final String _C_CNT_STR_   = "Consumer count";
	protected static final String _P_CNT_STR_   = "Producer run count";
	protected static final String _DREG_C_STR_  = "Deregistering consumers";
	protected static final String _P_C_CNT_STR_ = "Producer client count";
	protected static final String _DREG_P_STR_  = "Deregistering producers";
	protected static final String _DREG_P2_STR_ = "Deregistering producers (again)";

	protected static int _MSG_FILL_LENGTH_;
	
	static {
		String [] msgList = {_REG_P_STR_, _REG_C_STR_, _C_CNT_STR_, _P_CNT_STR_, _DREG_C_STR_,
							 _P_C_CNT_STR_, _DREG_P_STR_, _DREG_P2_STR_};
		int		  maxLen  = 0;
		
		for (String str: msgList)
			if (str.length () > maxLen)
				maxLen = str.length ();
		
		_MSG_FILL_LENGTH_ = maxLen + 3;
	}
	
	protected static void evalTest (String msg, Object expected, Object have)
	{
		boolean	     success = (expected.equals (have));
		@SuppressWarnings("resource")
		PrintStream  pStream = success ? System.out : System.err;
		@SuppressWarnings("resource")
		PrintStream  oStream = success ? System.err : System.out;
		StringBuffer strBuff = new StringBuffer (msg);
		
		oStream.flush ();
		
		for (int i = 0; i < _MSG_FILL_LENGTH_ - msg.length (); i++)
			strBuff.append (".");
		
		strBuff.append (success ? "PASSED" : "FAILED");

		if (!success)
			strBuff.append ("; expected " + expected + ", have " + have);

		pStream.println (strBuff.toString ());
		pStream.flush ();		
	}

	protected int registerProducers ()
	{
		_BaseProducer [] producerList = {new _Producer1 (), new _Producer12 (), new _Producer23 ()};
		int				 count		  = 0;
		
		for (_BaseProducer producer: producerList) {
			count += producer.registerProducer ();
			producers.add (producer);
		}

		return count;
	}

	protected int deregisterProducers ()
	{
		int count = 0;

		for (_BaseProducer producer: producers)
			count += producer.deregisterProducer ();

		return count;
	}
	
	protected int consumerCount ()
	{
		int count = 0;
		
		for (_BaseProducer producer: producers)
			count += producer.consumerCount ();
		
		return count;	
	}

	protected boolean registerConsumers ()
	{
		_BaseConsumer [] consumerList = {new _Consumer1 (), new _Consumer12 ()};
		boolean			 cRegistered  = true;
		
		for (_BaseConsumer consumer: consumerList) {
			consumers.add (consumer);
			cRegistered &= consumer.registerConsumer ();
		}
		
		return cRegistered;		
	}

	protected int deregisterConsumers ()
	{
		int count = 0;

		for (_BaseConsumer consumer: consumers)
			count += ProducerConsumer.getSingleton ().deregisterConsumer (consumer);
		
		return count;
	}

	protected int runCount ()
	{
		int count = 0;
		
		for (_BaseProducer producer: producers)
			count += producer.runCount ();
		
		return count;	
	}

	protected void run ()
	{
		evalTest (_REG_P_STR_,   5,	   registerProducers ());
		evalTest (_REG_C_STR_,   true, registerConsumers ());
		evalTest (_C_CNT_STR_,   6,    consumerCount ());
		evalTest (_P_CNT_STR_,	 6,	   runCount ());
		evalTest (_DREG_C_STR_,  3,	   deregisterConsumers ());
		evalTest (_P_C_CNT_STR_, 0,	   consumerCount ());
		evalTest (_DREG_P_STR_,	 5,	   deregisterProducers ());
		evalTest (_DREG_P2_STR_, 0,	   deregisterProducers ());
	}

	/**
	 * @param args
	 */
	public static void main (String[] args)
	{
		ProducerConsumerTest test = new ProducerConsumerTest ();
		
		test.run ();
	}

}
