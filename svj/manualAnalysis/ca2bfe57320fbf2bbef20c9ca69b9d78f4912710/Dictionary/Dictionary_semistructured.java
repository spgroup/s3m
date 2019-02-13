/**
 * IK ????  ?? 5.0
 * IK Analyzer release 5.0
 * 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ???????(linliangyi2005@gmail.com)??
 * ???? 2012???????
 * provided by Linliangyi and copyright 2012 by Oolong studio
 * 
 * 
 */
package org.wltea.analyzer.dic; 

import java.io.BufferedReader; 
import java.io.File; 
import java.io.FileInputStream; 
import java.io.FileNotFoundException; 
import java.io.IOException; 
import java.io.InputStream; 
import java.io.InputStreamReader; 
import java.nio.file.Path; 
import java.util.ArrayList; 
import java.util.Collection; 
import java.util.List; 
import java.util.concurrent.Executors; 
import java.util.concurrent.ScheduledExecutorService; 
import java.util.concurrent.TimeUnit; 

import org.apache.http.client.ClientProtocolException; 
import org.apache.http.client.config.RequestConfig; 
import org.apache.http.client.methods.CloseableHttpResponse; 
import org.apache.http.client.methods.HttpGet; 
import org.apache.http.impl.client.CloseableHttpClient; 
import org.apache.http.impl.client.HttpClients; 
import org.elasticsearch.common.io.PathUtils; 
import org.elasticsearch.common.logging.ESLogger; 
import org.elasticsearch.common.logging.Loggers; 
import org.wltea.analyzer.cfg.Configuration; 

/**
 * ?????,????
 */
public  class  Dictionary {
	


	/*
	 * ??????
	 */
	private static Dictionary singleton;
	

    private DictSegment _MainDict;
	

    private DictSegment _SurnameDict;
	

    private DictSegment _QuantifierDict;
	

    private DictSegment _SuffixDict;
	

    private DictSegment _PrepDict;
	

    private DictSegment _StopWords;
	

	
	/**
	 * ????
	 */
	private Configuration configuration;
	
    public static final ESLogger logger=Loggers.getLogger("ik-analyzer");
	
    
    private static ScheduledExecutorService pool = Executors.newScheduledThreadPool(1);
	
    
    public static final String PATH_DIC_MAIN = "ik/main.dic";
	
    public static final String PATH_DIC_SURNAME = "ik/surname.dic";
	
    public static final String PATH_DIC_QUANTIFIER = "ik/quantifier.dic";
	
    public static final String PATH_DIC_SUFFIX = "ik/suffix.dic";
	
    public static final String PATH_DIC_PREP = "ik/preposition.dic";
	
    public static final String PATH_DIC_STOP = "ik/stopword.dic";
	
    
    private Dictionary(){

	}
	

	/**
	 * ?????
	 * ??IK Analyzer?????Dictionary?????????????
	 * ???Dictionary?????????????????
	 * ?????????????
	 * ????????????????????????
	 * @return Dictionary
	 */
	public static synchronized Dictionary initial(Configuration cfg){
<<<<<<< C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var1_6285597805720452428.java
		
		synchronized(Dictionary.class){
			if(singleton == null){
				singleton = new Dictionary();
				singleton.configuration=cfg;
				singleton.loadMainDict();
				singleton.loadSurnameDict();
				singleton.loadQuantifierDict();
				singleton.loadSuffixDict();
				singleton.loadPrepDict();
				singleton.loadStopWordDict();
				
				//??????
				for(String location:cfg.getRemoteExtDictionarys()){
					//10 ???????????  60?????  ???
					pool.scheduleAtFixedRate(new Monitor(location), 10, 60, TimeUnit.SECONDS);
=======
		if(singleton == null){
			synchronized(Dictionary.class){
				if(singleton == null){
					singleton = new Dictionary();
					singleton.configuration=cfg;
					singleton.loadMainDict();
					singleton.loadSurnameDict();
					singleton.loadQuantifierDict();
					singleton.loadSuffixDict();
					singleton.loadPrepDict();
					singleton.loadStopWordDict();

					//??????
					for(String location:cfg.getRemoteExtDictionarys()){
						//10 ???????????  60?????  ???
						pool.scheduleAtFixedRate(new Monitor(location), 10, 60, TimeUnit.SECONDS);
					}
					for(String location:cfg.getRemoteExtStopWordDictionarys()){
						pool.scheduleAtFixedRate(new Monitor(location), 10, 60, TimeUnit.SECONDS);
					}

					return singleton;
>>>>>>> C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var2_4280970282520874771.java
				}
				for(String location:cfg.getRemoteExtStopWordDictionarys()){
					pool.scheduleAtFixedRate(new Monitor(location), 10, 60, TimeUnit.SECONDS);
				}
				
				return singleton;
			}
		}
		return singleton;
	}
	
	
	/**
	 * ????????
	 * @return Dictionary ????
	 */
	public static Dictionary getSingleton(){
		if(singleton == null){
			throw new IllegalStateException("????????????initial??");
		}
		return singleton;
	}
	
	
	/**
	 * ???????
	 * @param words Collection<String>????
	 */
	public void addWords(Collection<String> words){
		if(words != null){
			for(String word : words){
				if (word != null) {
					//?????????????
					singleton._MainDict.fillSegment(word.trim().toCharArray());
				}
			}
		}
	}
	
	
	/**
	 * ??????????
	 */
	public void disableWords(Collection<String> words){
		if(words != null){
			for(String word : words){
				if (word != null) {
					//??????
					singleton._MainDict.disableSegment(word.trim().toCharArray());
				}
			}
		}
	}
	
	
	/**
	 * ???????
	 * @return Hit ??????
	 */
	public Hit matchInMainDict(char[] charArray){
		return singleton._MainDict.match(charArray);
	}
	
	
	/**
	 * ???????
	 * @return Hit ??????
	 */
	public Hit matchInMainDict(char[] charArray , int begin, int length){
		return singleton._MainDict.match(charArray, begin, length);
	}
	
	
	/**
	 * ????????
	 * @return Hit ??????
	 */
	public Hit matchInQuantifierDict(char[] charArray , int begin, int length){
		return singleton._QuantifierDict.match(charArray, begin, length);
	}
	
	
	
	/**
	 * ?????Hit?????DictSegment???????
	 * @return Hit
	 */
	public Hit matchWithHit(char[] charArray , int currentIndex , Hit matchedHit){
		DictSegment ds = matchedHit.getMatchedDictSegment();
		return ds.match(charArray, currentIndex, 1 , matchedHit);
	}
	
	
	
	/**
	 * ????????
	 * @return boolean
	 */
	public boolean isStopWord(char[] charArray , int begin, int length){
		return singleton._StopWords.match(charArray, begin, length).isMatch();
	}
		
	
	/**
	 * ??????????
	 */
	private void loadMainDict(){
		//?????????
		_MainDict = new DictSegment((char)0);

		//???????
		Path file = PathUtils.get(configuration.getDictRoot(), Dictionary.PATH_DIC_MAIN);

		InputStream is = null;
		try {
			is = new FileInputStream(file.toFile());
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage(), e);
		}

		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(is , "UTF-8"), 512);
			String theWord = null;
			do {
				theWord = br.readLine();
				if (theWord != null && !"".equals(theWord.trim())) {
					_MainDict.fillSegment(theWord.trim().toCharArray());
				}
			} while (theWord != null);

		} catch (IOException e) {
			logger.error("ik-analyzer",e);

		}finally{
			try {
				if(is != null){
					is.close();
					is = null;
				}
			} catch (IOException e) {
				logger.error("ik-analyzer",e);
			}
		}
		//??????
		this.loadExtDict();
		//?????????
		this.loadRemoteExtDict();
	}
		
	
	/**
	 * ????????????????
	 */
	private void loadExtDict(){
		//????????
		List<String> extDictFiles  = configuration.getExtDictionarys();
		if(extDictFiles != null){
			InputStream is = null;
			for(String extDictName : extDictFiles){
				//????????
				logger.info("[Dict Loading] " + extDictName);
				Path file = PathUtils.get(configuration.getDictRoot(), extDictName);
				try {
					is = new FileInputStream(file.toFile());
				} catch (FileNotFoundException e) {
					logger.error("ik-analyzer",e);
				}

				//??????????????
				if(is == null){
					continue;
				}
				try {
					BufferedReader br = new BufferedReader(new InputStreamReader(is , "UTF-8"), 512);
					String theWord = null;
					do {
						theWord = br.readLine();
						if (theWord != null && !"".equals(theWord.trim())) {
							//???????????????
							_MainDict.fillSegment(theWord.trim().toCharArray());
						}
					} while (theWord != null);

				} catch (IOException e) {
					logger.error("ik-analyzer",e);
				}finally{
					try {
<<<<<<< C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var1_3674378106757564627.java
						is.close();
						is = null;
=======
						if(is != null){
							is.close();
							is = null;
						}
>>>>>>> C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var2_8052868433849843477.java
					} catch (IOException e) {
						logger.error("ik-analyzer",e);
					}
				}
			}
		}
	}
	
	
	
	/**
	 * ?????????????
	 */
	private void loadRemoteExtDict(){
		List<String> remoteExtDictFiles  = configuration.getRemoteExtDictionarys();
		for(String location:remoteExtDictFiles){
			logger.info("[Dict Loading] " + location);
			List<String> lists = getRemoteWords(location);
			
			/** Redundant Nullcheck as the list is initialized in the getRemoteWords method
			//??????????????
			if(lists == null){
				logger.error("[Dict Loading] "+location+"????");
				continue;
			}*/
			
			for(String theWord:lists){
				if (theWord != null && !"".equals(theWord.trim())) {
					//???????????????
					logger.info(theWord);
					_MainDict.fillSegment(theWord.trim().toLowerCase().toCharArray());
				}
			}
		}

	}
	
	
	/**
	 * ??????????????
	 */
	private static List<String> getRemoteWords(String location){

		List<String> buffer = new ArrayList<String>();
		RequestConfig rc = RequestConfig.custom().setConnectionRequestTimeout(10*1000)
				.setConnectTimeout(10*1000).setSocketTimeout(60*1000).build();
		CloseableHttpClient httpclient = HttpClients.createDefault();
		CloseableHttpResponse response;
		BufferedReader in;
		HttpGet get = new HttpGet(location);
		get.setConfig(rc);
		try {
			response = httpclient.execute(get);
			if(response.getStatusLine().getStatusCode()==200){

				String charset = "UTF-8";
				//????????utf-8
				if(response.getEntity().getContentType().getValue().contains("charset=")){
					String contentType=response.getEntity().getContentType().getValue();
					charset=contentType.substring(contentType.lastIndexOf("=")+1);
				}
				in = new BufferedReader(new InputStreamReader(response.getEntity().getContent(),charset));
				String line ;
				while((line = in.readLine())!=null){
					buffer.add(line);
				}
				in.close();
				response.close();
				return buffer;
			}
			response.close();
		} catch (ClientProtocolException e) {
			logger.error( "getRemoteWords {} error" , e , location);
		} catch (IllegalStateException e) {
			logger.error( "getRemoteWords {} error" , e , location );
		} catch (IOException e) {
			logger.error( "getRemoteWords {} error" , e , location );
		}
		return buffer;
	}
	
	
	
	
	/**
	 * ????????????
	 */
	private void loadStopWordDict(){
		//???????
		_StopWords = new DictSegment((char)0);

		//???????
		Path file = PathUtils.get(configuration.getDictRoot(), Dictionary.PATH_DIC_STOP);

		InputStream is = null;
		try {
			is = new FileInputStream(file.toFile());
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage(), e);
		}

		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(is , "UTF-8"), 512);
			String theWord = null;
			do {
				theWord = br.readLine();
				if (theWord != null && !"".equals(theWord.trim())) {
					_StopWords.fillSegment(theWord.trim().toCharArray());
				}
			} while (theWord != null);

		} catch (IOException e) {
			logger.error("ik-analyzer",e);

		}finally{
			try {
				if(is != null){
					is.close();
					is = null;
				}
			} catch (IOException e) {
				logger.error("ik-analyzer",e);
			}
		}


		//????????
		List<String> extStopWordDictFiles  = configuration.getExtStopWordDictionarys();
		if(extStopWordDictFiles != null){
			is = null;
			for(String extStopWordDictName : extStopWordDictFiles){
				logger.info("[Dict Loading] " + extStopWordDictName);

				//????????
				file=PathUtils.get(configuration.getDictRoot(), extStopWordDictName);
				try {
					is = new FileInputStream(file.toFile());
				} catch (FileNotFoundException e) {
					logger.error("ik-analyzer",e);
				}
				//??????????????
				if(is == null){
					continue;
				}
				try {
					BufferedReader br = new BufferedReader(new InputStreamReader(is , "UTF-8"), 512);
					String theWord = null;
					do {
						theWord = br.readLine();
						if (theWord != null && !"".equals(theWord.trim())) {
							//??????????????
							_StopWords.fillSegment(theWord.trim().toCharArray());
						}
					} while (theWord != null);

				} catch (IOException e) {
					logger.error("ik-analyzer",e);

				}finally{
					try {
<<<<<<< C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var1_2658589671629193343.java
						is.close();
						is = null;
=======
						if(is != null){
							is.close();
							is = null;
						}
>>>>>>> C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var2_669313918257397529.java
					} catch (IOException e) {
						logger.error("ik-analyzer",e);
					}
				}
			}
		}

		//????????
		List<String> remoteExtStopWordDictFiles  = configuration.getRemoteExtStopWordDictionarys();
		for(String location:remoteExtStopWordDictFiles){
			logger.info("[Dict Loading] " + location);
			List<String> lists = getRemoteWords(location);
			
			/** Redundant Nullcheck as the list is initialized in the getRemoteWords method
			//??????????????
			if(lists == null){
				logger.error("[Dict Loading] "+location+"????");
				continue;
			}*/
			
			for(String theWord:lists){
				if (theWord != null && !"".equals(theWord.trim())) {
					//?????????????
					logger.info(theWord);
					_StopWords.fillSegment(theWord.trim().toLowerCase().toCharArray());
				}
			}
		}


	}
	
	
	/**
	 * ??????
	 */
	private void loadQuantifierDict(){
		//?????????
		_QuantifierDict = new DictSegment((char)0);
		//????????
		Path file = PathUtils.get(configuration.getDictRoot(), Dictionary.PATH_DIC_QUANTIFIER);
		InputStream is = null;
		try {
			is = new FileInputStream(file.toFile());
		} catch (FileNotFoundException e) {
			logger.error("ik-analyzer",e);
		}
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(is , "UTF-8"), 512);
			String theWord = null;
			do {
				theWord = br.readLine();
				if (theWord != null && !"".equals(theWord.trim())) {
					_QuantifierDict.fillSegment(theWord.trim().toCharArray());
				}
			} while (theWord != null);

		} catch (IOException ioe) {
			logger.error("Quantifier Dictionary loading exception.");

		}finally{
			try {
				if(is != null){
					is.close();
					is = null;
				}
			} catch (IOException e) {
				logger.error("ik-analyzer",e);
			}
		}
	}
	


    private void loadSurnameDict(){

		_SurnameDict = new DictSegment((char)0);
		Path file = PathUtils.get(configuration.getDictRoot(), Dictionary.PATH_DIC_SURNAME);
<<<<<<< C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var1_3683676398592275077.java
        InputStream is = null;
        try {
            is = new FileInputStream(file.toFile());
        } catch (FileNotFoundException e) {
            logger.error("ik-analyzer",e);
        }
        if(is == null){
            throw new RuntimeException("Surname Dictionary not found!!!");
        }
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is , "UTF-8"), 512);
            String theWord;
            do {
                theWord = br.readLine();
                if (theWord != null && !"".equals(theWord.trim())) {
                    _SurnameDict.fillSegment(theWord.trim().toCharArray());
                }
            } while (theWord != null);
        } catch (IOException e) {
            logger.error("ik-analyzer",e);
        }finally{
            try {
				is.close();
				is = null;
            } catch (IOException e) {
                logger.error("ik-analyzer",e);
            }
        }
    }=======
		InputStream is = null;
		try {
			is = new FileInputStream(file.toFile());
		} catch (FileNotFoundException e) {
			logger.error("ik-analyzer",e);
		}
		if(is == null){
			throw new RuntimeException("Surname Dictionary not found!!!");
		}
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(is , "UTF-8"), 512);
			String theWord;
			do {
				theWord = br.readLine();
				if (theWord != null && !"".equals(theWord.trim())) {
					_SurnameDict.fillSegment(theWord.trim().toCharArray());
				}
			} while (theWord != null);
		} catch (IOException e) {
			logger.error("ik-analyzer",e);
		}finally{
			try {
				if(is != null){
					is.close();
					is = null;
				}
			} catch (IOException e) {
				logger.error("ik-analyzer",e);
			}
		}
	}>>>>>>> C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var2_7253007493867124724.java

	


    private void loadSuffixDict(){

		_SuffixDict = new DictSegment((char)0);
		Path file = PathUtils.get(configuration.getDictRoot(), Dictionary.PATH_DIC_SUFFIX);
		InputStream is = null;
		try {
			is = new FileInputStream(file.toFile());
		} catch (FileNotFoundException e) {
			logger.error("ik-analyzer",e);
		}
		if(is == null){
			throw new RuntimeException("Suffix Dictionary not found!!!");
		}
		try {

			BufferedReader br = new BufferedReader(new InputStreamReader(is , "UTF-8"), 512);
			String theWord;
			do {
				theWord = br.readLine();
				if (theWord != null && !"".equals(theWord.trim())) {
					_SuffixDict.fillSegment(theWord.trim().toCharArray());
				}
			} while (theWord != null);
		} catch (IOException e) {
			logger.error("ik-analyzer",e);
		}finally{
			try {
				is.close();
				is = null;
			} catch (IOException e) {
				logger.error("ik-analyzer",e);
			}
		}
	}
	


    private void loadPrepDict(){

		_PrepDict = new DictSegment((char)0);
		Path file = PathUtils.get(configuration.getDictRoot(), Dictionary.PATH_DIC_PREP);
		InputStream is = null;
		try {
			is = new FileInputStream(file.toFile());
		} catch (FileNotFoundException e) {
			logger.error("ik-analyzer",e);
		}
		if(is == null){
			throw new RuntimeException("Preposition Dictionary not found!!!");
		}
		try {

			BufferedReader br = new BufferedReader(new InputStreamReader(is , "UTF-8"), 512);
			String theWord;
			do {
				theWord = br.readLine();
				if (theWord != null && !"".equals(theWord.trim())) {

					_PrepDict.fillSegment(theWord.trim().toCharArray());
				}
			} while (theWord != null);
		} catch (IOException e) {
			logger.error("ik-analyzer",e);
		}finally{
			try {
				is.close();
				is = null;
			} catch (IOException e) {
				logger.error("ik-analyzer",e);
			}
		}
	}
	
    
    public void reLoadMainDict(){
		logger.info("??????...");
		// ???????????????????????????
		Dictionary tmpDict = new Dictionary();
		tmpDict.configuration = getSingleton().configuration;
		tmpDict.loadMainDict();
		tmpDict.loadStopWordDict();
		_MainDict = tmpDict._MainDict;
		_StopWords = tmpDict._StopWords;
		logger.info("????????...");
	}

}

