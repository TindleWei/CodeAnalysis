/**
The MIT License (MIT)

Copyright (c) 2014 singwhatiwanna
https://github.com/singwhatiwanna
http://blog.csdn.net/singwhatiwanna

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package com.lianshequ.widget.pinheader;

import java.util.NoSuchElementException;

import com.lianshequ.activity.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class StickyLayout extends LinearLayout {
	private static final String TAG = "StickyLayout";

	public interface OnStickyHeaderListener {
		public boolean giveUpTouchEvent(MotionEvent event);

		public boolean onRefresh();
	}

	private View mHeader;
	private View mContent;
	private OnStickyHeaderListener onStickyHeaderListener;

	// header的高度 单位：px
	private int mOriginalHeaderHeight;
	private int mHeaderHeight;

	private int mStatus = STATUS_EXPANDED;
	public static final int STATUS_EXPANDED = 1;
	public static final int STATUS_COLLAPSED = 2;

	private int mTouchSlop;

	// 分别记录上次滑动的坐标
	private int mLastX = 0;
	private int mLastY = 0;

	// 分别记录上次滑动的坐标(onInterceptTouchEvent)
	private int mLastXIntercept = 0;
	private int mLastYIntercept = 0;

	// 用来控制滑动角度，仅当角度a满足如下条件才进行滑动：tan a = deltaX / deltaY > 2
	private static final int TAN = 2;

	private boolean mIsSticky = true;
	private boolean mInitDataSucceed = false;
	private boolean mDisallowInterceptTouchEventOnHeader = true;

	private Context context;

	private ImageView mArrowImageView;
	private ProgressBar mProgressBar;
	private TextView mHintTextView;

	private Animation mRotateUpAnim;
	private Animation mRotateDownAnim;

	private final int ROTATE_ANIM_DURATION = 180;

	private int mState = STATE_NORMAL;
	public final static int STATE_NORMAL = 0;
	public final static int STATE_READY = 1;
	public final static int STATE_REFRESHING = 2;

	public StickyLayout(Context context) {
		super(context);
		initView(context);
	}

	public StickyLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView(context);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public StickyLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView(context);
	}

	/**
	 * @must 注意还有initData()
	 */
	public void initView(Context context) {
		this.context = context;

		mRotateUpAnim = new RotateAnimation(0.0f, -180.0f,
				Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
				0.5f);
		mRotateUpAnim.setDuration(ROTATE_ANIM_DURATION);
		mRotateUpAnim.setFillAfter(true);
		mRotateDownAnim = new RotateAnimation(-180.0f, 0.0f,
				Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
				0.5f);
		mRotateDownAnim.setDuration(ROTATE_ANIM_DURATION);
		mRotateDownAnim.setFillAfter(true);
	}

	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
		super.onWindowFocusChanged(hasWindowFocus);
		if (hasWindowFocus && (mHeader == null || mContent == null)) {
			initData();
		}
	}

	private void initData() {
		int headerId = getResources().getIdentifier("sticky_header", "id",
				getContext().getPackageName());
		int contentId = getResources().getIdentifier("sticky_content", "id",
				getContext().getPackageName());
		if (headerId != 0 && contentId != 0) {
			mHeader = findViewById(headerId);
			mContent = findViewById(contentId);
			mOriginalHeaderHeight = mHeader.getMeasuredHeight();

			mHeaderHeight = mOriginalHeaderHeight;
			smoothSetHeaderHeight(mHeaderHeight, 0, 0);
			mHeader.setVisibility(View.VISIBLE);

			mTouchSlop = ViewConfiguration.get(getContext())
					.getScaledTouchSlop();
			if (mHeaderHeight > 0) {
				mInitDataSucceed = true;
			}
		}

		mArrowImageView = (ImageView) mHeader
				.findViewById(R.id.xlistview_header_arrow);
		mHintTextView = (TextView) mHeader
				.findViewById(R.id.xlistview_header_hint_textview);
		mProgressBar = (ProgressBar) mHeader
				.findViewById(R.id.xlistview_header_progressbar);
	}

	public void setOnGiveUpTouchEventListener(OnStickyHeaderListener l) {
		onStickyHeaderListener = l;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		int intercepted = 0;
		int x = (int) event.getX();
		int y = (int) event.getY();

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN: {
			mLastXIntercept = x;
			mLastYIntercept = y;
			mLastX = x;
			mLastY = y;
			intercepted = 0;
			break;
		}
		case MotionEvent.ACTION_MOVE: {
			int deltaX = x - mLastXIntercept;
			int deltaY = y - mLastYIntercept;
			if (mDisallowInterceptTouchEventOnHeader && y <= getHeaderHeight()) {
				intercepted = 0;
			} else if (Math.abs(deltaY) <= Math.abs(deltaX)) {
				intercepted = 0;
			} else if (mStatus == STATUS_EXPANDED && deltaY <= -mTouchSlop) {
				intercepted = 1;
			} else if (onStickyHeaderListener != null) {
				if (onStickyHeaderListener.giveUpTouchEvent(event)
						&& deltaY >= mTouchSlop) {
					intercepted = 1;
				}
			}
			break;
		}
		case MotionEvent.ACTION_UP: {
			intercepted = 0;
			mLastXIntercept = mLastYIntercept = 0;
			break;
		}
		default:
			break;
		}
		return intercepted != 0 && mIsSticky;
	}

	public void setState(int state) {
		if (state == mState)
			return;

		if (state == STATE_REFRESHING) { // 显示进度
			mArrowImageView.clearAnimation();
			mArrowImageView.setVisibility(View.INVISIBLE);
			mProgressBar.setVisibility(View.VISIBLE);
		} else { // 显示箭头图片
			mArrowImageView.setVisibility(View.VISIBLE);
			mProgressBar.setVisibility(View.INVISIBLE);
		}

		switch (state) {
		case STATE_NORMAL:
			if (mState == STATE_READY) {
				mArrowImageView.startAnimation(mRotateDownAnim);
			}
			if (mState == STATE_REFRESHING) {
				mArrowImageView.clearAnimation();
			}
			mHintTextView.setText(R.string.xlistview_header_hint_normal);
			break;
		case STATE_READY:
			if (mState != STATE_READY) {
				mArrowImageView.clearAnimation();
				mArrowImageView.startAnimation(mRotateUpAnim);
				mHintTextView.setText(R.string.xlistview_header_hint_ready);
			}
			break;
		case STATE_REFRESHING:
			mHintTextView.setText(R.string.xlistview_header_hint_loading);
			break;
		default:
		}

		mState = state;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!mIsSticky) {
			return true;
		}
		int x = (int) event.getX();
		int y = (int) event.getY();
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN: {
			break;
		}
		case MotionEvent.ACTION_MOVE: {
			int deltaX = x - mLastX;
			int deltaY = y - mLastY;
			mHeaderHeight += deltaY;
			setHeaderHeight(mHeaderHeight);

			if (mHeaderHeight >= mOriginalHeaderHeight) {
				setState(XListViewHeader.STATE_READY);
			} else {
				setState(XListViewHeader.STATE_NORMAL);
			}

			break;
		}
		case MotionEvent.ACTION_UP: {
			// 这里做了下判断，当松开手的时候，会自动向两边滑动，具体向哪边滑，要看当前所处的位置
			int destHeight = 0;
			mStatus = STATUS_COLLAPSED;

			if (mHeaderHeight <= mOriginalHeaderHeight) {
				// 慢慢滑向终点
				this.smoothSetHeaderHeight(mHeaderHeight, destHeight, 500);
			} else {
				setState(XListViewHeader.STATE_REFRESHING);

				if (onStickyHeaderListener != null)
					onStickyHeaderListener.onRefresh();

				smoothSetHeaderHeight(mHeaderHeight, mOriginalHeaderHeight, 500);

			}
			// if (mHeaderHeight <= mOriginalHeaderHeight * 0.5) {
			// destHeight = 0;
			// mStatus = STATUS_COLLAPSED;
			// } else {
			// destHeight = mOriginalHeaderHeight;
			// mStatus = STATUS_EXPANDED;
			// }

			break;
		}
		default:
			break;
		}
		mLastX = x;
		mLastY = y;
		return true;
	}

	/**
	 * 当刷新结束时调用
	 */
	public void scrollHeaderBack() {
		if (mState == STATE_REFRESHING) {
			mState = STATE_NORMAL;
			smoothSetHeaderHeight(mOriginalHeaderHeight, 0, 500);
        }
	}

	public void smoothSetHeaderHeight(final int from, final int to,
			long duration) {
		smoothSetHeaderHeight(from, to, duration, false);
	}

	public void smoothSetHeaderHeight(final int from, final int to,
			long duration, final boolean modifyOriginalHeaderHeight) {
		final int frameCount = (int) (duration / 1000f * 30) + 1;
		final float partation = (to - from) / (float) frameCount;
		new Thread("Thread#smoothSetHeaderHeight") {

			@Override
			public void run() {
				for (int i = 0; i < frameCount; i++) {
					final int height;
					if (i == frameCount - 1) {
						height = to;
					} else {
						height = (int) (from + partation * i);
					}
					post(new Runnable() {
						public void run() {
							setHeaderHeight(height);
						}
					});
					try {
						sleep(10);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}

				if (modifyOriginalHeaderHeight) {
					setOriginalHeaderHeight(to);
				}
			};

		}.start();
	}

	public void setOriginalHeaderHeight(int originalHeaderHeight) {
		mOriginalHeaderHeight = originalHeaderHeight;
	}

	public void setHeaderHeight(int height, boolean modifyOriginalHeaderHeight) {
		if (modifyOriginalHeaderHeight) {
			setOriginalHeaderHeight(height);
		}
		setHeaderHeight(height);
	}

	public void setHeaderHeight(int height) {
		if (!mInitDataSucceed) {
			initData();
		}

		if (height <= 0) {
			height = 0;
		} else if (height > mOriginalHeaderHeight) {
			// 日后研究一下如何在这里做阻尼变化
			// int delta = height - mOriginalHeaderHeight;
			// height = (int)(delta *0.5) + mOriginalHeaderHeight;
		}

		if (height == 0) {
			mStatus = STATUS_COLLAPSED;
		} else {
			mStatus = STATUS_EXPANDED;
		}

		if (mHeader != null && mHeader.getLayoutParams() != null) {
			mHeader.getLayoutParams().height = height;
			mHeader.requestLayout();
			mHeaderHeight = height;
		}
	}

	public int getHeaderHeight() {
		return mHeaderHeight;
	}

	public void setSticky(boolean isSticky) {
		mIsSticky = isSticky;
	}

	public void requestDisallowInterceptTouchEventOnHeader(
			boolean disallowIntercept) {
		mDisallowInterceptTouchEventOnHeader = disallowIntercept;
	}

}