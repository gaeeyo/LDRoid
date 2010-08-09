package net.fushihara.LDRoid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.fushihara.LDRoid.LDRClient.Feeds;
import net.fushihara.LDRoid.LDRClient.Subscribe;
import net.fushihara.LDRoid.PrefetchUnReadFeedsTask.OnPrefetchUnReadFeedsListener;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ListView;
import android.widget.Toast;

public class Main extends ListActivity implements OnPrefetchUnReadFeedsListener {
	private static final String TAG = "Main";
    public static final String KEY_LOGIN_ID = "login_id";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_SUBS_ID  = "subs_id";
    public static final String KEY_SUBS_TITLE = "subs_title";
    public static final String KEY_SUBS_UNREAD_COUNT = "subs_unread_count";
    private static final int PREFETCH_COUNT = 5;
    
    private static final int REQUEST_SETTING = 1;
    private static final int REQUEST_FEEDVIEW = 2;

	private SubscribeLocalList subs;
	private PrefetchUnReadFeedsTask prefetch_task;
	private int prefetch_start_position;
	private int prefetch_limit;
	private ProgressDialog prefetch_dialog;
	private LDRoidApplication application;

	private UnReadFeedsCache feeds_cache;
	private int subs_position;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);

        feeds_cache = UnReadFeedsCache.getInstance(getApplicationContext());
        
        // �ۑ�����Ă��� subs ��ǂݍ���
        application = (LDRoidApplication)getApplication();
        setSubs(application.getSubscribeLocalList());
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    }
    
	private void loadSubs() {
		Log.d(TAG, "loadSubs");

		LDRClient client = application.getClient();
		if ( client == null ) {
			// ID/PW���ݒ�
	    	showSetting();
        	return;
		}

		GetSubsTask task = new GetSubsTask(this);
		task.execute(client);
	}
	
	public void onGetSubsTaskCompleted(List<Subscribe> result, Exception error) {
		if (error != null) {
			Toast.makeText(this, 
					"ERROR: " + error.getMessage(), 
					Toast.LENGTH_LONG).show();
			return;
		}
		
		
		
		if (result == null) {
			Toast.makeText(this, "no feed", Toast.LENGTH_LONG).show();
		}
		
		feeds_cache.clear();

		// subs �����[�g����
		Collections.sort(result, new Comparator<Subscribe>() {
			@Override
			public int compare(Subscribe object1, Subscribe object2) {
				return object2.rate - object1.rate;
			}
		});
		
		// �ǂݍ��� subs ���t�@�C���ɏ����o��
		application.saveSubscribeListToFile((ArrayList<Subscribe>)result);
		
		SubscribeLocalList subsLocal = new SubscribeLocalList(result);
		application.setSubscribeLocalList(subsLocal);
		//removeSubsLocalFile(subsLocal);
		setSubs(subsLocal);
	}
	
	private void setSubs(SubscribeLocalList newSubs) {
		subs = newSubs;
		
		// �L���b�V���ς݂��ǂ����̃t���O��ݒ�
		ArrayList<String> cachedList = feeds_cache.getList();
		int cachedList_size = cachedList.size();
		for (int j=0; j<cachedList_size; j++) {
			SubscribeLocal sl = subs.getItemById(cachedList.get(j));
			if (sl != null) {
				sl.setPrefetched(true);
			}
		}
		
		SubsAdapter adapter = new SubsAdapter(this, subs);
		setListAdapter(adapter);
        prefetchStart(0, PREFETCH_COUNT);
	}
	
	private void showSetting() {
     	Intent intent = new Intent(this, Setting.class);
     	startActivityForResult(intent, REQUEST_SETTING);
	}
	
	private void prefetchStart(int start_position, int limit) {
		prefetch_start_position = start_position;
		prefetch_limit = limit;
		
		if (prefetch_dialog != null) {
			prefetch_dialog.setMax(limit);
		}
		
		if (!prefetchNext()) {
			prefetchFinish();
		}
	}
	
	// �t�B�[�h�̐�ǂ�
	private boolean prefetchNext() {
		if (prefetch_task != null) {
			return false;
		}
		
		int position = prefetch_start_position;
		SubsAdapter adapter = (SubsAdapter)getListAdapter();
		// �t�B�[�h���󂩁A���X�g�͈̔͊O���w�肳�ꂽ�Ƃ��͐�ǂ݂��Ȃ�
		if (position < 0 || position >= adapter.getCount()) {
			return false;
		}

		LDRClient client = application.getClient();
		if (client == null) {
			return false;
		}
		
		// ��ǂ݊J�n�ʒu����L���b�V���̑��݂��m�F���āA�L���b�V�����Ȃ���΃^�X�N���N��
		int last = Math.min(adapter.getCount(), position + prefetch_limit);

		for (; position<last; position++) {
			SubscribeLocal sub = (SubscribeLocal)adapter.getItem(position);
			if (!sub.isPrefetched() && !feeds_cache.isExists(sub.getSubscribeId())) {
				// �L���b�V�����쐬����Ă��Ȃ����̂���������^�X�N���N��
				Log.d(TAG, "prefetch " + position);
				prefetch_task = new PrefetchUnReadFeedsTask(client, this);
				setProgressBarIndeterminateVisibility(true);
				prefetch_task.execute(sub.getSubscribeId());

				// �u���ׂĎ擾�v�Ń_�C�A���O���\������Ă���Ƃ��͐i�����X�V
				if (prefetch_dialog != null && prefetch_dialog.isShowing() && last > 0) {
					prefetch_dialog.setProgress(position);
				}
				
				return true;
			}
		}
		return false;
	}
	
	private void prefetchCancel() {
		prefetch_limit = 0;
	}

	// prefetchStart �Ŏn�܂������삪���������Ƃ��̏���
	private void prefetchFinish() {
		// �u���ׂĎ擾�v�̃_�C�A���O���\������Ă��������
		if (prefetch_dialog != null) {
			prefetch_dialog.dismiss();
			prefetch_dialog = null;
		}
	}
	
	// �t�B�[�h�̐�ǂ݊���
	@Override
	public void onPrefetchUnReadFeedsTaskComplete(Object sender, 
			String subscribe_id, Feeds feeds, Exception e) {

		setProgressBarIndeterminateVisibility(false);
		// �G���[��������Εۑ�����
		prefetch_task = null;
		
		if (e == null) {
			// TODO: �t�@�C���̏����o���܂�AsyncTask�ł�����ق���
			// �p�t�H�[�}���X���ǂ��Ǝv�����AFeedView �ŏ������݂�
			// �����ɔ�������\��������̂Ń��C���X���b�h�Ŏ��s
			feeds_cache.put(subscribe_id, feeds);
			
			SubscribeLocal sl = subs.getItemById(subscribe_id);
			if (sl != null) {
				sl.setPrefetched(true);
				getListView().invalidateViews();
			}
			
			// ���̐�ǂ݂��J�n
			if (!prefetchNext()) {
				prefetchFinish();
			}
		}
	}
	
	@Override
	protected void onDestroy() {
    	application.saveSubscribeLocalListToFile();
		super.onDestroy();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    super.onCreateOptionsMenu(menu);
	    getMenuInflater().inflate(R.menu.main_menu, menu);
	    return true;
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean ret = true;
        switch (item.getItemId()) {
        default:
            ret = super.onOptionsItemSelected(item);
            break;
        case R.id.menu_reload:
            loadSubs();
            break;
        case R.id.menu_setting:
        	showSetting();
        	break;
        case R.id.menu_fetch_all:
        	fetchAll();
        }
        return ret;
    }
    
    // ���ׂĎ擾
    private void fetchAll() {
    	prefetch_dialog = new ProgressDialog(this);
    	prefetch_dialog.setMessage(getString(R.string.dlg_fetch_all_title));
    	prefetch_dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    	prefetch_dialog.setButton(getString(R.string.dlg_cancel), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				prefetchCancel();
			}
		});
    	prefetch_dialog.show();
    	prefetchStart(0, getListAdapter().getCount());
	}

	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	
    	Log.d(TAG, "onActivityResult");
    	switch (requestCode) {
    	case REQUEST_SETTING:
    		// �ݒ��ʂ���A���Ă�����A�J�E���g���ύX����Ă��Ȃ����m�F����
    		LDRClient client = application.getClient();
    		if (!client.getAccount().equals(application.getAccount())) {
        		// �A�J�E���g���ύX����Ă�����A�V�����A�J�E���g�ōĎ擾����
    			// TODO:
    			application.clearClient();
				loadSubs();
    		}
    		break;
    	case REQUEST_FEEDVIEW:
    		if (data != null) {
    			String subs_id = data.getStringExtra(KEY_SUBS_ID);
    			if (subs_id != null) {
        			// FeedView�ŃL���b�V�����쐬���ꂽ���m�F
    				if (feeds_cache.isExists(subs_id)) {
    					SubscribeLocal sl = subs.getItemById(subs_id);
    					if (sl != null) {
    						sl.setPrefetched(true);
    	    		        getListView().invalidateViews();
    					}
    				}
    			}
    		}
    		switch (resultCode) {
    		case FeedView.RESULT_PREV:
    			showNextFeed(-1);
    			break;
    		case FeedView.RESULT_NEXT:
    			showNextFeed(1);
    			break;
    		}
    		break;
    	}
    }
    
	
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        
        // TODO: ������ prefetch_task �Ŏ擾���̃t�B�[�h��
        // �N���b�N���ꂽ�ꍇ�́Aprefetch_task �̏I����҂��Ȃ��Ɠ�d�ɓǂݍ��݂�
        // �������Ă��܂��̂ŏ������ʂɑ҂������
        
        subs_position = position;
        
        Intent i = new Intent(this, FeedView.class);
        SubscribeLocal sub = subs.get(position);
        i.putExtra(KEY_SUBS_ID, sub.getSubscribeId());
        i.putExtra(KEY_SUBS_TITLE, sub.getTitle());
        i.putExtra(KEY_SUBS_UNREAD_COUNT, sub.getUnreadCount());
        startActivityForResult(i, REQUEST_FEEDVIEW);

        prefetchStart(position + 1, PREFETCH_COUNT);
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	switch (keyCode) {
    	case KeyEvent.KEYCODE_S:
    		showNextFeed(0);
    		break;
    	}
    	return super.onKeyDown(keyCode, event);
    }

    private void showNextFeed(int direction) {
    	ListView list = getListView();
    	if (list.getCount() == 0) return;

    	// �J�[�\�����ړ�����
    	subs_position += direction;
    	//Log.d(TAG, "position:"+subs_position);
    	if (subs_position < 0 || subs_position >= list.getCount()) {
    		// �I�[�ɒB�����牽�����Ȃ�
    		return ;
    	}

    	// �ړ����I�����āA�N���b�N�������Ƃɂ��� 
    	list.setSelectionFromTop(subs_position, list.getMeasuredHeight() / 3);
    	list.performItemClick(list, subs_position, 0);
    }
}
