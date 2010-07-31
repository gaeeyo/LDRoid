package net.fushihara.LDRoid;

import java.util.List;
import net.fushihara.LDRoid.LDRClient.Feed;
import net.fushihara.LDRoid.LDRClient.Feeds;
import android.app.ProgressDialog;
import android.os.AsyncTask;

public class GetUnReadFeedsTask extends AsyncTask<String, Void, Feeds> {

	private LDRClient client;
	private FeedView view;
	private ProgressDialog progressDialog;
	private Exception error = null;
	
	public GetUnReadFeedsTask(FeedView view, LDRClient client) {
		this.view   = view;
		this.client = client;
	}
	
	@Override
	protected void onPreExecute() {
        progressDialog = new ProgressDialog(view);  
        progressDialog.setTitle("�t�B�[�h�Ǎ���");  
        progressDialog.setIndeterminate(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);  
        progressDialog.show();  
		super.onPreExecute();
	}
	
	@Override
	protected Feeds doInBackground(String... sub_ids) {
		try {
			return client.unRead(sub_ids[0]);
		}
		catch (Exception e) {
			e.printStackTrace();
			error = e;
			return null;
		}
	}

	@Override
	protected void onPostExecute(Feeds result) {
		progressDialog.dismiss();
		view.onGetUnReadFeedsTaskCompleted(result, error);
	}
}
