package net.fushihara.LDRoid;

import net.fushihara.LDRoid.LDRClient.Feed;
import android.os.AsyncTask;
import android.widget.Toast;

public class SetPinTask extends AsyncTask<Feed, Void, String> {

	private FeedView  view;
	private LDRClient client;
	
	public SetPinTask(FeedView view, LDRClient client) {
		this.view   = view;
		this.client = client;
	}
	
	@Override
	protected String doInBackground(Feed... feeds) {
		client.pin_add(feeds[0]);
		return feeds[0].title;
	}

	@Override
	protected void onPostExecute(String result) {
		Toast.makeText(view, "�w"+result+"�x���s���ɓo�^���܂���", Toast.LENGTH_LONG).show();
	}
}
