package net.fushihara.LDRoid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;


public class LDRClient {
	private static final String TAG = "LDRClient";
	public static class Subscribe extends Object implements Serializable {
		private static final long serialVersionUID = 3249600979937544481L;
		public List<String> tags = new ArrayList<String>();
		public String folder;
		public String subscribe_id;
		public String icon;
		public String title;
		public int rate;
		public String link;
		public int subscribers_count;
		public int unread_count;

		public Subscribe(JSONObject obj) throws JSONException {
			folder = obj.getString("folder");
			subscribe_id = obj.getString("subscribe_id");
			icon = obj.getString("icon");
			title = obj.getString("title");
			link = obj.getString("link");
			rate = obj.getInt("rate");
			subscribers_count = obj.getInt("subscribers_count");
			unread_count = obj.getInt("unread_count");
			JSONArray json = obj.getJSONArray("tags");
			for (int i = 0; i < json.length(); i++) {
			    JSONObject jo = json.getJSONObject(i);
			    tags.add(jo.toString());
			}
		}
	}
	
	public static class Feed extends Object implements Serializable {
		private static final long serialVersionUID = -7847969794608002809L;
		public String title;
		public String author;
		public String link;
		public long date;
		public String body;

		public Feed(JSONObject obj) throws JSONException {
			title = obj.getString("title");
			author = obj.getString("author");
			link = obj.getString("link");
			body = obj.getString("body");
			date = obj.optLong("modified_on");
		}
	}
	
	public static class Feeds extends ArrayList<Feed> {
		private static final long serialVersionUID = -6411496025853141474L;
		long last_stored_on;
	}
	
	private static String auth_url = "https://member.livedoor.com/login/";
	private static String domain = "http://reader.livedoor.com/";
	private CookieStore cookie_store;
	
	private LDRClientAccount account;
	private String session_id = null;

	public LDRClient(LDRClientAccount account) {
		super();
		
		this.account = account;
	}
	
	public LDRClientAccount getAccount() {
		return account;
	}
	
	private HttpClient createDefaultClient() {
		DefaultHttpClient client = new DefaultHttpClient();
		client.setCookieStore(cookie_store);
		return client;
	}
	
	public List<Subscribe> subs(int unread) throws Exception {
		login();
		

		HttpPost req = new HttpPost(domain + "/api/subs");
        List<NameValuePair> params = new ArrayList<NameValuePair>(1);
        
        params.add(new BasicNameValuePair("unread", String.valueOf(unread)));
        params.add(new BasicNameValuePair("ApiKey", session_id));

        req.setEntity(new UrlEncodedFormEntity(params));
		HttpClient client = createDefaultClient();
        HttpResponse response = client.execute(req);
		
		JSONArray json = new JSONArray(getContent(response));
		List<Subscribe> items = new ArrayList<Subscribe>();
		for (int i = 0; i < json.length(); i++) {
		    JSONObject obj = json.getJSONObject(i);
		    items.add(new Subscribe(obj));
		}
		
		return items;
	}
	
	public Feeds unRead(String subscribe_id) throws Exception {

		login();

		HttpPost req = new HttpPost(domain + "/api/unread");
        List<NameValuePair> params = new ArrayList<NameValuePair>(1);
        
        params.add(new BasicNameValuePair("subscribe_id", subscribe_id));
        params.add(new BasicNameValuePair("ApiKey", session_id));

		req.setEntity(new UrlEncodedFormEntity(params));
		HttpClient client = createDefaultClient();
        HttpResponse response = client.execute(req);

        JSONObject jsonroot = new JSONObject(getContent(response));
        JSONArray json = jsonroot.getJSONArray("items");
		Feeds items = new Feeds();
		for (int i = 0; i < json.length(); i++) {
		    JSONObject obj = json.getJSONObject(i);
		    items.add(new Feed(obj));
		}

		items.last_stored_on = 0;
		if (jsonroot.has("last_stored_on")) {
			// items が空の場合は last_stored_on が無い
			items.last_stored_on = jsonroot.getLong("last_stored_on");
		}
		
		return items;
	}
	
	public void touchAll(String subscribe_id) throws Exception {
		
		login();

		HttpPost req = new HttpPost(domain + "/api/touch_all");
        List<NameValuePair> params = new ArrayList<NameValuePair>(1);
        
        params.add(new BasicNameValuePair("subscribe_id", subscribe_id));
        params.add(new BasicNameValuePair("ApiKey", session_id));

		req.setEntity(new UrlEncodedFormEntity(params));
		HttpClient client = createDefaultClient();
        client.execute(req);
	}
	
	public void touch(String subscribe_id, long timestamp) throws Exception {
		
		login();

		HttpPost req = new HttpPost(domain + "/api/touch_all");
        List<NameValuePair> params = new ArrayList<NameValuePair>(1);
        
        params.add(new BasicNameValuePair("subscribe_id", subscribe_id));
        params.add(new BasicNameValuePair("timestamp", Long.toString(timestamp)));
        params.add(new BasicNameValuePair("ApiKey", session_id));

		req.setEntity(new UrlEncodedFormEntity(params));
		HttpClient client = createDefaultClient();
        client.execute(req);
	}
	
	public void pin_add(Feed feed) throws Exception {
		
		login();

		HttpPost req = new HttpPost(domain + "/api/pin/add");
        List<NameValuePair> params = new ArrayList<NameValuePair>(1);
        
        params.add(new BasicNameValuePair("title", feed.title));
        params.add(new BasicNameValuePair("link", feed.link));
        params.add(new BasicNameValuePair("ApiKey", session_id));

		req.setEntity(new UrlEncodedFormEntity(params, "utf-8"));
		HttpClient client = createDefaultClient();
        HttpResponse response = client.execute(req);
        
        JSONObject jsonroot = new JSONObject(getContent(response));
        if (jsonroot.getInt("isSuccess") != 1) { 
	        int errorCode = jsonroot.getInt("ErrorCode");
	        throw new Exception("ErrorCode: " + Integer.toString(errorCode));
        }
	}
	
	// HttpResponseのbodyを取得
	private String getContent(HttpResponse response) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream(10*1024);
			response.getEntity().writeTo(baos);
	        
	        return baos.toString("UTF-8");
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}

	private void login() throws Exception {
		if (session_id != null) {
			return;
		}
		Log.d(TAG, "login");
		
		HttpPost request = new HttpPost(auth_url);
		
		// ログインのために必要なパラメータをセット
		// .next, .svが無いと上手くいかない模様
        List<NameValuePair> params = new ArrayList<NameValuePair>(1);
        
        params.add(new BasicNameValuePair("livedoor_id", account.getLoginId()));
        params.add(new BasicNameValuePair("password", account.getPassword()));
        params.add(new BasicNameValuePair(".next", "http://reader.livedoor.com/reader/"));
        params.add(new BasicNameValuePair(".sv", "reader"));
        
		request.setEntity(new UrlEncodedFormEntity(params));
		
		// ログイン
		HttpClient client = createDefaultClient();
		HttpResponse response = client.execute(request);

		// LDRセッションIDの取得
		Header[] headers = response.getHeaders("Set-Cookie");
		for(int i=0; i<headers.length; i++) {
			String val = headers[i].getValue();
			Pattern p = Pattern.compile("reader_sid=(.+?);");
			Matcher m = p.matcher(val);
			
			if ( m.find() ) {
				session_id = m.group(1);
				cookie_store = ((DefaultHttpClient)client).getCookieStore();
			}
		}
		
		if (session_id == null) {
			HttpEntity entity = response.getEntity();
			ByteArrayOutputStream content = new ByteArrayOutputStream();
			new BufferedHttpEntity(entity).writeTo(content);
			
			// (今現在 euc-jp になっている)
			String encoding = "UTF-8";
			if (entity.getContentType().getValue().toLowerCase().indexOf("euc-jp") != -1) {
				encoding = "euc-jp";
			}
			// エラーメッセージを見つける
			String html = new String(content.toByteArray(), encoding);
			Pattern p = Pattern.compile(" class=\"error-messages\".*?>(.*?)</");
			Matcher m = p.matcher(html);
	
			if (m.find()) {
				throw new Exception("Login error(" + m.group(1) +")");
			}
				
			throw new Exception("Login error");
		}
	}
	
}
