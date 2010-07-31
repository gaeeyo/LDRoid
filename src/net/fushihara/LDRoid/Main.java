package net.fushihara.LDRoid;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.fushihara.LDRoid.LDRClient.Feeds;
import net.fushihara.LDRoid.LDRClient.Subscribe;
import net.fushihara.LDRoid.PrefetchUnReadFeedsTask.OnPrefetchUnReadFeedsListener;
import android.app.ListActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class Main extends ListActivity implements OnPrefetchUnReadFeedsListener {
	private static final String TAG = "Main";
    public static final String KEY_LOGIN_ID = "login_id";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_SUBS_ID  = "subs_id";
    public static final String KEY_SUBS_TITLE = "subs_title";
    private static final String SUBS_FILE = "subs";
    private static final int PREFETCH_COUNT = 5;
    
    private static final int REQUEST_SETTING = 1;
    private static final int REQUEST_FEEDVIEW = 2;

	private static final int MENU_RELOAD_ID  = 0;
	private static final int MENU_SETTING_ID = 1;

	private List<Subscribe> subs;
	private boolean isSubsSaved = true;
	private PrefetchUnReadFeedsTask prefetch_task;
	private int prefetch_start_position;

	private LDRClient client;
	private UnReadFeedsCache feeds_cache;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);

        feeds_cache = UnReadFeedsCache.getInstance(getApplicationContext());
        
        // �ۑ�����Ă��� subs ���Z�b�g
        setSubs(loadSubsFromFile());
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	
    	//loadSubs();
    }
    
	private void loadSubs() {
		Log.d(TAG, "loadSubs");
		if ( client == null ) {
		    LDRClientAccount account = getAccount();

		    if (account.isEmpty()) {
				// ID/PW���ݒ�
		    	showSetting();
	        	return;
			}
			client = new LDRClient(account);
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
		saveSubsToFile((ArrayList<Subscribe>)result);
		
		setSubs(result);
	}
	
	private void setSubs(List<Subscribe> newSubs) {
		
		subs = newSubs;
		
		SubsAdapter adapter = new SubsAdapter(this, subs);
		setListAdapter(adapter);
		
		prefetch_start_position = 0;
        prefetch();
	}
	
	private void showSetting() {
     	Intent intent = new Intent(this, Setting.class);
     	startActivityForResult(intent, REQUEST_SETTING);
	}
	
	private LDRClientAccount getAccount() {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		LDRClientAccount account = new LDRClientAccount(
				pref.getString("login_id", null),
				pref.getString("password", null)
			);
		return account;
	}
	
	// �t�B�[�h�̐�ǂ�
	private void prefetch() {
		if (prefetch_task != null) {
			return;
		}
		
		int position = prefetch_start_position;
		ListAdapter adapter = getListAdapter();
		// �t�B�[�h���󂩁A���X�g�͈̔͊O���w�肳�ꂽ�Ƃ��͐�ǂ݂��Ȃ�
		if (position < 0 || position >= adapter.getCount()) {
			return;
		}

		if (client == null) {
			LDRClientAccount account = getAccount();
			if (account.isEmpty()) return;
			
			client = new LDRClient(account);
		}
		
		// ��ǂ݊J�n�ʒu���� PREFETCH_COUNT ���̃t�B�[�h��
		// �L���b�V���̑��݂��m�F���āA�L���b�V�����Ȃ���΃^�X�N���N��
		for (int j=0; j<PREFETCH_COUNT; j++) {
			Subscribe sub = (Subscribe) adapter.getItem(position);
			if (!feeds_cache.isExists(sub.subscribe_id)) {
				// �L���b�V�����쐬����Ă��Ȃ����̂���������^�X�N���N��
				Log.d(TAG, "prefetch " + position);
				prefetch_task = new PrefetchUnReadFeedsTask(client, this);
				setProgressBarIndeterminateVisibility(true);
				prefetch_task.execute(sub.subscribe_id);
				break;
			}
			if (++position >= adapter.getCount()) {
				break;
			}
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
			
			// ���̐�ǂ݂��J�n
			prefetch();
		}
	}
	
	
	@SuppressWarnings("unchecked")
	private List<Subscribe> loadSubsFromFile() {
		FileInputStream fis = null;
		ObjectInputStream ois = null;
		ArrayList<Subscribe> result = null;
		
		try {
			fis = openFileInput(SUBS_FILE);
			ois = new ObjectInputStream(fis);
			result = (ArrayList<Subscribe>)ois.readObject();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		Log.d(TAG, "loadSubsFromFile " + (result != null));
		
		return result;
	}
	
	private void saveSubsToFile(List<Subscribe> subs) {
		Log.d(TAG, "saveSubsToFile");
		isSubsSaved = true;
		FileOutputStream fos = null;
		ObjectOutputStream oos = null;
		try {
			fos = openFileOutput(SUBS_FILE, MODE_PRIVATE);
			oos = new ObjectOutputStream(fos);
			oos.writeObject(subs);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (oos != null) {
				try {
					oos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		if (!isSubsSaved) {
			saveSubsToFile(subs);
			isSubsSaved = true;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    super.onCreateOptionsMenu(menu);
 	    menu.add(0, MENU_RELOAD_ID, 0, R.string.menu_reload).setIcon(android.R.drawable.ic_menu_rotate);
 	    menu.add(0, MENU_SETTING_ID, 0, R.string.menu_setting).setIcon(android.R.drawable.ic_menu_manage);
	    return true;
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean ret = true;
        switch (item.getItemId()) {
        default:
            ret = super.onOptionsItemSelected(item);
            break;
        case MENU_RELOAD_ID:
            loadSubs();
            break;
        case MENU_SETTING_ID:
        	showSetting();
        	break;
        }
        return ret;
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	
    	Log.d(TAG, "onActivityResult");
    	switch (requestCode) {
    	case REQUEST_SETTING:
    		// �ݒ��ʂ���A���Ă�����A�J�E���g���ύX����Ă��Ȃ����m�F����
    		if (client != null) {
        		// �A�J�E���g���ύX����Ă�����A�V�����A�J�E���g�ōĎ擾����
    			LDRClientAccount newAccount = getAccount();
    			if (!newAccount.equals(client.getAccount())) {
    				client = null;
    				loadSubs();
    			}
    		}
    		break;
    	case REQUEST_FEEDVIEW:
    		// FeedView ����߂����Ƃ��A���ǉ�����Ă�����(RESULT_OK)
    		// subs �� unread_count �� 0 �ɂ���
    		if (resultCode == RESULT_OK && data != null) {
    			String subs_id = data.getStringExtra(KEY_SUBS_ID);
    			if (subs_id != null) {
    				resetUnreadCount(subs_id);
    			}
    		}
    		break;
    	}
    }
    
    private void resetUnreadCount(String subs_id)  {
    	int subs_size = subs.size();
		for (int j=0; j<subs_size; j++) {
			Subscribe sub = subs.get(j); 
			if (sub.subscribe_id.equals(subs_id)) {
				if (sub.unread_count > 0) {
					sub.unread_count = 0;
					getListView().invalidateViews();
					isSubsSaved = false;
					// TODO: isSubsSaved �� false �̏ꍇ��
					// onStop ���Ă΂��ƁA�t�B�[�h�ꗗ���t�@�C���ɏ����o����邪�A
					// �t�B�[�h�̐��������Ȃ�Ɩ��ʂ��傫���̂ŁA�t�B�[�h�ꗗ��
					// unread_count == 0 ���ǂ����̏��͕����ĊǗ����������ǂ���������Ȃ�
					// (���Ƃ��� unread_count == 0 �� subscribe_id �̃��X�g��ۑ�����Ƃ�)
				}
				break;
			}
		}
    }
	
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        
        // TODO: ������ prefetch_task �Ŏ擾���̃t�B�[�h��
        // �N���b�N���ꂽ�ꍇ�́Aprefetch_task �̏I����҂��Ȃ��Ɠ�d�ɓǂݍ��݂�
        // �������Ă��܂��̂ŏ������ʂɑ҂������
        
        Intent i = new Intent(this, FeedView.class);
        Subscribe sub = subs.get(position);
        LDRClientAccount account = getAccount();
		i.putExtra(KEY_LOGIN_ID, account);
        i.putExtra(KEY_SUBS_ID, sub.subscribe_id);
        i.putExtra(KEY_SUBS_TITLE, sub.title);
        startActivityForResult(i, REQUEST_FEEDVIEW);
        
        prefetch_start_position = position + 1;
        prefetch();
    }

    
}
