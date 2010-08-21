package net.fushihara.LDRoid;

import java.util.ArrayList;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

class SubsAdapter extends BaseAdapter implements GetIconTask.OnGetIconListener {
	
	@SuppressWarnings("unused")
	private static final String TAG = "SubsAdapter";
	
	public static final byte FLAG_PREFETCHED = 0x01;

	private static int [] rateColors;
	private static int title_normal;
	private static int title_empty;
	private static int title_prefetched;
	
	private SubscribeLocalList items;
	private LayoutInflater inflater;
	
	private GetIconTask get_icon_task;
	private ArrayList<View> views;
	private boolean feed_icon_visibility; 
	
	public SubsAdapter(Context context, SubscribeLocalList subs) {
		items = subs;
		
		inflater = (LayoutInflater)context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);
		if (items == null) {
			items = new SubscribeLocalList();
		}
		
		if (rateColors == null) {
	        Resources res = context.getResources();
	        rateColors = new int [] {
	        		res.getColor(R.color.rate0),
	        		res.getColor(R.color.rate1),
	        		res.getColor(R.color.rate2),
	        		res.getColor(R.color.rate3),
	        		res.getColor(R.color.rate4),
	        		res.getColor(R.color.rate5),
	        };
	        TypedArray a = context.obtainStyledAttributes(R.styleable.SubsAdapter);
	        title_normal = a.getColor(R.styleable.SubsAdapter_feedTitleColorNormal, 0xFFD7D7D7);
	        title_empty = a.getColor(R.styleable.SubsAdapter_feedTitleColorEmpty, 0xFF707070);
	        title_prefetched = a.getColor(R.styleable.SubsAdapter_feedTitleColorPrefetched, 0xFF00D7D7);
		}
		
		views = new ArrayList<View>();
	}
	
	@Override
	public int getCount() {
		return items.size();
	}

	@Override
	public Object getItem(int position) {
		return items.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view = convertView;
		ViewHolder holder;
		if (convertView == null) {
			view = inflater.inflate(R.layout.subs_row, null);
			views.add(view);
			
			holder = new ViewHolder(view);
			view.setTag(holder);
		}
		else {
			holder = (ViewHolder)view.getTag(); 
		}
		
		SubscribeLocal s = items.get(position);
		holder.subs_local = s;
		
		TextView t = holder.title;
		holder.title.setText(s.getTitle());
		if (s.getUnreadCount() == 0) {
			t.setTextColor(title_empty);
		}
		else if (s.isPrefetched()) {
			t.setTextColor(title_prefetched);
		}
		else {
			t.setTextColor(title_normal);
		}
		
		holder.setIconVisibility(feed_icon_visibility);
		if (feed_icon_visibility) {
			Bitmap bmp = GetIconTask.getCache(s.getIcon());
			if (bmp != null) {
				// �A�C�R�����L���b�V���ς݂̏ꍇ�͂����ɐݒ�
				holder.setBitmap(bmp);
			}
			else {
				// �A�C�R���̃L���b�V�����Ȃ��ꍇ�͂Ƃ肠������ɐݒ�
				holder.setBitmap(null);
	
				if (get_icon_task == null) {
					// ���s���̃A�C�R���擾�^�X�N�������ꍇ�́A�^�X�N���J�n����
					get_icon_task = new GetIconTask(this);
					get_icon_task.execute(s.getIcon());
				}
			}
		}
		
		holder.setUnreadCount(s.getUnreadCount());
		
		t = holder.ratebar;
		int rate = s.getRate();
		if (rate >= 0 && rate <= 5) {
			t.setBackgroundColor(rateColors[rate]);
		}
		else {
			t.setBackgroundColor(rateColors[0]);
		}
		
		return view;
	}

	public void setFeedIconVisibility(boolean visible) {
		feed_icon_visibility = visible;
	}
	
	// Adapter����������View��Tag�ɕۑ�����N���X
	// ���X�g��View��Tag�ɂ���Object��ۑ����Ă����A�X�N���[����ĕ`���getView��
	// �Ăяo����邽�т�findViewById �������x�����ʂɎ��s�����̂�h��
	private static class ViewHolder {
		public TextView title;
		public TextView count;
		public ImageView icon;
		public TextView ratebar;
		public SubscribeLocal subs_local;
		
		public ViewHolder(View view) {
			title = (TextView)view.findViewById(R.id.title);
			icon = (ImageView)view.findViewById(R.id.icon);
			count = (TextView)view.findViewById(R.id.count);
			ratebar = (TextView)view.findViewById(R.id.ratebar);
		}
		
		public void setUnreadCount(int unreadCount) {
			if (unreadCount > 0) {
				count.setText(Integer.toString(unreadCount));
				count.setVisibility(View.VISIBLE);
			}
			else {
				count.setVisibility(View.GONE);
			}
		}

		public void setIconVisibility(boolean feedIconVisibility) {
			icon.setVisibility(feedIconVisibility ? View.VISIBLE : View.GONE);
		}

		public void setBitmap(Bitmap bmp) {
			if (bmp != null) {
				icon.setImageBitmap(bmp);
				
				BitmapDrawable bd = (BitmapDrawable)icon.getDrawable();
				bd.setFilterBitmap(false);
			}
			else {
				icon.setImageDrawable(null);
			}
		}
	}

	// �A�C�R���̎擾����
	@Override
	public void onGetIconTaskComplete(Object sender, String uri, Bitmap result) {
		get_icon_task = null;

		// �擾�����A�C�R����K�v�Ƃ��Ă���view��T��
		if (result != null) {
			int views_size = views.size();
			ViewHolder holder;
			for (int j=0; j<views_size; j++) {
				holder = (ViewHolder)views.get(j).getTag();
				if (holder.subs_local.getIcon() == uri) {
					holder.setBitmap(result);
				}
			}
			getNextIcon();
		}
		else {
			// �G���[�Ȃǂŉ摜���擾�ł��Ȃ������Ƃ��� getNextIcon() ���Ă΂Ȃ�
			// �ĂԂƉi�v�ɓ����摜��v�����Ă��܂�
		}
	}
	
	// �A�C�R�����ǂݍ��܂�Ă��Ȃ��r���[������΁A�V�����^�X�N���J�n����
	private void getNextIcon() {
		int views_size = views.size();
		ViewHolder holder;
		for (int j=0; j<views_size; j++) {
			holder = (ViewHolder)views.get(j).getTag();
			if (holder.icon.getDrawable() == null) {
				get_icon_task = new GetIconTask(this);
				get_icon_task.execute(holder.subs_local.getIcon());
				break;
			}
		}
	}
}
