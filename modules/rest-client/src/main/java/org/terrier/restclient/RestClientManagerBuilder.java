package org.terrier.restclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.terrier.querying.IndexRef;
import org.terrier.querying.Manager;
import org.terrier.querying.ManagerFactory;
import org.terrier.querying.ScoredDoc;
import org.terrier.querying.ScoredDocList;
import org.terrier.querying.SearchRequest;

public class RestClientManagerBuilder implements ManagerFactory.Builder {

	@Override
	public boolean supports(IndexRef ref) {
		return ref.toString().startsWith("http");
	}

	@Override
	public Manager fromIndex(IndexRef ref) {
		return new RESTManagerProxy(ref);
	}
	
	class RESTRequest implements SearchRequest
	{
		private static final long serialVersionUID = 1L;
		String matching, wmodel, qid, query;
		Map<String,String> controls = new HashMap<>();
		ScoredDocList results;
		long starttime;
		

		@Override
		public void addMatchingModel(String MatchingModelName,
				String WeightingModelName) {
			matching = MatchingModelName;
			wmodel = WeightingModelName;
		}

		@Override
		public void setQueryID(String _qid) {
			qid = _qid;
		}

		@Override
		public void setControl(String Name, String Value) {
			controls.put(Name, Value);
		}

		@Override
		public String getQueryID() {
			return qid;
		}

		@Override
		public String getControl(String Name) {
			return controls.get(Name);
		}

		@Override
		public boolean isEmpty() {
			return query.length() == 0;
		}

		@Override
		public void setOriginalQuery(String q) {
			query = q;
		}

		@Override
		public String getOriginalQuery() {
			return query;
		}

		@Override
		public void setNumberOfDocumentsAfterFiltering(int n) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getNumberOfDocumentsAfterFiltering() {
			return results.size();
		}

		@Override
		public long getStartedProcessingTime() {
			return starttime;
		}

		@Override
		public void setStartedProcessingTime(long time) {
			starttime = time;
		}

		@Override
		public ScoredDocList getResults() {
			return results;
		}

		@Override
		public void setContextObject(String key, Object value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Object getContextObject(String key) {
			return null;
		}
		
	}
	
	class RESTManagerProxy implements Manager
	{
		IndexRef ref;
		
		RESTManagerProxy(IndexRef _ref){
			this.ref = _ref;
		}
		
		@Override
		public SearchRequest newSearchRequest() {
			return new RESTRequest();
		}

		@Override
		public SearchRequest newSearchRequest(String QueryID) {
			SearchRequest srq = new RESTRequest();
			srq.setQueryID(QueryID);
			return srq;
		}

		@Override
		public SearchRequest newSearchRequest(String QueryID, String query) {
			SearchRequest srq = new RESTRequest();
			srq.setQueryID(QueryID);
			srq.setOriginalQuery(query);
			return srq;
		}

		@Override
		public SearchRequest newSearchRequestFromQuery(String query) {
			SearchRequest srq = new RESTRequest();
			srq.setOriginalQuery(query);
			return srq;
		}

		@Override
		public void setProperty(String key, String value) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void setProperties(Properties p) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void runSearchRequest(SearchRequest srq) {
			String url = null;
			try{
				srq.setStartedProcessingTime(System.currentTimeMillis());
				url = ref.toString()+ "/search/trec?" + "query=" + URLEncoder.encode(srq.getOriginalQuery(), "UTF-8");
				Map<String,String> controls = ((RESTRequest)srq).controls;
				if (controls.size() > 0)
				{
					url = url + "&controls=" + URLEncoder.encode(
						controls.entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue()).collect(Collectors.joining(", ")), "UTF-8");
				}
				CloseableHttpClient httpclient = HttpClients.createDefault();
				HttpGet httpGet = new HttpGet(url);
				CloseableHttpResponse response = httpclient.execute(httpGet);
				BufferedReader br = null;
				try{
					int code = response.getStatusLine().getStatusCode();
					if (code != 200)
						throw new IOException("Could not contact REST server at "+url+" : " + code);
					
					br = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
					
									
					ScoredDocList rtr = new ScoredDocListType();
					Map<String,Integer> metaOffset = new HashMap<String,Integer>();
					metaOffset.put("docno", 0);
					
					String line;	
					while ( (line = br.readLine()) != null)
					{
						line = line.trim();
						String[] parts = line.split("\\s+", 6);
						rtr.add(new ScoredDoc(0, Double.parseDouble(parts[4]), (short)0, new String[]{parts[2]}, metaOffset));
					}
					
					((RESTRequest)srq).results = rtr;
				} finally {
					if (br != null)
						br.close();
					response.close();
				}
			} catch (Exception e) {
				throw new RuntimeException("Could not access " + url, e);
			}
		}

		@Override
		public IndexRef getIndexRef() {
			return ref;
		}
		
	}
	
	static class ScoredDocListType extends ArrayList<ScoredDoc> implements ScoredDocList 
	{
		private static final long serialVersionUID = 1L;

		@Override
		public String[] getMetaKeys() {
			return new String[]{"docno"};
		}
		
	}

}
