package com.lian.pinheader.widget.pinheader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ExpandableListView;
import android.widget.AbsListView.OnScrollListener;

public class PinnedHeaderExpandableListView extends ExpandableListView
		implements OnScrollListener {

	private static final String TAG = "PinnedHeaderExpandableListView";

	private OnScrollListener mScrollListener;

	private OnHeaderUpdateListener mHeaderUpdateListener;

	public interface OnHeaderUpdateListener {
		/**
		 * 返回一个view对象即可 
		 * @must view必须要有LayoutParams
		 */
		public View getPinnedHeader();

		public void updatePinnedHeader(View headerView, int firstVisibleGroupPos);
	}

	/**
	 * 这个Header就是悬停的Header
	 */
	private View pinHeaderView;
	private int pinHeaderWidth;
	private int pinHeaderHeight;

	/**
	 * 在pinHeader布局点击的view
	 */
	private View pinTouchedView;

	/**
	 * 判断headerView是否被点击
	 */
	private boolean isActionDown = false;

	protected boolean isHeaderGroupClicked = true;

	public PinnedHeaderExpandableListView(Context context) {
		super(context);
		initView(context);
	}

	public PinnedHeaderExpandableListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView(context);
	}

	public PinnedHeaderExpandableListView(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
		initView(context);
	}

	private void initView(Context context) {
		setFadingEdgeLength(0);
		setOnScrollListener(this);
	}

	@Override
	public void setOnScrollListener(OnScrollListener l) {
		if (l != this) {
			mScrollListener = l;
		} else {
			mScrollListener = null;
		}
		super.setOnScrollListener(this);
	}

	/**
	 * 给group添加点击事件监听
	 * 
	 */
	public void setOnGroupClickListener(
			OnGroupClickListener onGroupClickListener,
			boolean isHeaderGroupClickable) {
		isHeaderGroupClicked = isHeaderGroupClickable;
		super.setOnGroupClickListener(onGroupClickListener);
	}

	public void setOnHeaderUpdateListener(OnHeaderUpdateListener listener) {
		mHeaderUpdateListener = listener;
		if (listener == null) {
			pinHeaderView = null;
			pinHeaderWidth = pinHeaderHeight = 0;
			return;
		}

		// 这行代码很关键，告诉我们 headerView来自哪
		pinHeaderView = listener.getPinnedHeader();

		int firstVisiblePos = getFirstVisiblePosition();
		int firstVisibleGroupPos = getPackedPositionGroup(getExpandableListPosition(firstVisiblePos));
		listener.updatePinnedHeader(pinHeaderView, firstVisibleGroupPos);

		requestLayout();
		postInvalidate();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if (pinHeaderView == null) {
			return;
		}
		measureChild(pinHeaderView, widthMeasureSpec, heightMeasureSpec);
		pinHeaderWidth = pinHeaderView.getMeasuredWidth();
		pinHeaderHeight = pinHeaderView.getMeasuredHeight();
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed, l, t, r, b);
		if (pinHeaderView == null) {
			return;
		}
		int delta = pinHeaderView.getTop();
		pinHeaderView.layout(0, delta, pinHeaderWidth, pinHeaderHeight + delta);
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);
		if (pinHeaderView != null) {
			drawChild(canvas, pinHeaderView, getDrawingTime());
		}
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		int x = (int) ev.getX();
		int y = (int) ev.getY();
		int pos = pointToPosition(x, y);

		// 如果滑动 header, 触摸事件被截取，列表不移动
		if (pinHeaderView != null && y >= pinHeaderView.getTop()
				&& y <= pinHeaderView.getBottom()) {

			if (ev.getAction() == MotionEvent.ACTION_DOWN) {

				// 获取点击的对象：是子控件 or header整体
				pinTouchedView = getTouchTarget(pinHeaderView, x, y);
				isActionDown = true;

			} else if (ev.getAction() == MotionEvent.ACTION_UP) {

				View touchTarget = getTouchTarget(pinHeaderView, x, y);
				
				if (touchTarget == pinTouchedView && pinTouchedView.isClickable()) {
					//优先处理header中子控件的点击事件
					pinTouchedView.performClick();
					invalidate(new Rect(0, 0, pinHeaderWidth, pinHeaderHeight));
					
				} else if (isHeaderGroupClicked) {
					//处理header的点击
					int groupPosition = getPackedPositionGroup(getExpandableListPosition(pos));
					if (groupPosition != INVALID_POSITION
							&& isActionDown) {
						if (isGroupExpanded(groupPosition)) {
							collapseGroup(groupPosition);
						} else {
							expandGroup(groupPosition);
						}
					}
				}
				isActionDown = false;
			}
			return true;
		}
		return super.dispatchTouchEvent(ev);
	}

	/**
	 * 这个地方是判断header的点击事件的，
	 * 尽管我认为作者费劲费神，写的事倍功半，
	 * 但是还是有可以借鉴的地方
	 */
	private View getTouchTarget(View view, int x, int y) {
		if (!(view instanceof ViewGroup)) {
			return view;
		}

		ViewGroup parent = (ViewGroup) view;
		int childrenCount = parent.getChildCount();
		
		//我认为这个Boolean多此一举
		final boolean customOrder = isChildrenDrawingOrderEnabled();
		View target = null;
		for (int i = childrenCount - 1; i >= 0; i--) {
			final int childIndex = customOrder ? getChildDrawingOrder(
					childrenCount, i) : i;
			final View child = parent.getChildAt(childIndex);
			if (isTouchPointInView(child, x, y)) {
				target = child;
				break;
			}
		}
		if (target == null) {
			target = parent;
		}

		return target;
	}

	private boolean isTouchPointInView(View view, int x, int y) {
		if (view.isClickable() && y >= view.getTop() && y <= view.getBottom()
				&& x >= view.getLeft() && x <= view.getRight()) {
			return true;
		}
		return false;
	}

	public void requestRefreshHeader() {
		refreshHeader();
		invalidate(new Rect(0, 0, pinHeaderWidth, pinHeaderHeight));
	}

	/**
	 * header悬停的关键方法
	 */
	protected void refreshHeader() {
		if (pinHeaderView == null) {
			return;
		}
		int firstVisiblePos = getFirstVisiblePosition();
		int pos = firstVisiblePos + 1;
		int firstVisibleGroupPos = getPackedPositionGroup(getExpandableListPosition(firstVisiblePos));
		int group = getPackedPositionGroup(getExpandableListPosition(pos));

		if (group == firstVisibleGroupPos + 1) {
			// 如果顶端是两个GroupView相邻
			View view = getChildAt(1);
			if (view == null) {
				return;
			}
			if (view.getTop() <= pinHeaderHeight) {
				//如果发生了两个GroupView的顶撞，则
				
				int delta = pinHeaderHeight - view.getTop();
				pinHeaderView.layout(0, -delta, pinHeaderWidth, pinHeaderHeight
						- delta);
			}
			/*else {
				// 其实这个处理完全没有必要，所以去掉了
				// mHeaderView.layout(0, 0, mHeaderWidth, mHeaderHeight);
			}*/
		} else {
			// 如果只有一个GroupView
			pinHeaderView.layout(0, 0, pinHeaderWidth, pinHeaderHeight);
		}

		if (mHeaderUpdateListener != null) {
			mHeaderUpdateListener.updatePinnedHeader(pinHeaderView,
					firstVisibleGroupPos);
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		if (mScrollListener != null) {
			mScrollListener.onScrollStateChanged(view, scrollState);
		}
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem,
			int visibleItemCount, int totalItemCount) {
		if (totalItemCount > 0) {
			refreshHeader();
		}
		if (mScrollListener != null) {
			mScrollListener.onScroll(view, firstVisibleItem, visibleItemCount,
					totalItemCount);
		}
	}

}
