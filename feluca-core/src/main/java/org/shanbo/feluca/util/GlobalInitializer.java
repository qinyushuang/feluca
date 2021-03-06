package org.shanbo.feluca.util;

import org.shanbo.feluca.common.ClusterUtil;
import org.shanbo.feluca.common.FelucaException;
import org.shanbo.feluca.node.http.HttpClientUtil;

public class GlobalInitializer {
	static{
		try{
			ClusterUtil.getFDFSAddress();
			HttpClientUtil.get().getHttpClient();
		}catch(Exception e){
			throw new FelucaException("init glocal components error", e);
		}
	}
	
	public static void call(){
		;
	}
	
	public static void shutdown(){
		HttpClientUtil.get().getHttpClient().getConnectionManager().shutdown();
	}
}
