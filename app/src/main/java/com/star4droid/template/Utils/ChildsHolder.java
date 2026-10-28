package com.star4droid.template.Utils;

import java.util.ArrayList;

/**
 * The parent / children links of one runtime item.
 * {@link #linkVersion} grows every time a link changes, so the GroupFollower knows when to rebuild its graph.
 */
public class ChildsHolder {
	/** Bumped on every link change (setParent / addChild / detach). */
	public static int linkVersion = 0;
	
	private PlayerItem playerItem;
	private ChildsHolder parent;
	/** Set once the item was destroyed (guards against destroying it twice). */
	public boolean destroyed = false;
	public ArrayList<PlayerItem> childs=new ArrayList<>();
	public ChildsHolder(PlayerItem item){
		playerItem=item;
	}
	
	public ChildsHolder getParentHolder(){
		return parent;
	}
	
	/** True when {@code other} is this holder or one of its ancestors (used to refuse cycles). */
	private boolean hasAncestorOrSelf(ChildsHolder other){
		int guard = 0;
		ChildsHolder h = this;
		while(h != null && guard++ < 1000){
			if(h == other) return true;
			h = h.parent;
		}
		return false;
	}
	
	public void setParent(ChildsHolder childsHolder){
		if(childsHolder == parent) {
			// keep the child list of the parent in sync
			if(parent != null && !parent.childs.contains(playerItem)) parent.childs.add(playerItem);
			return;
		}
		if(childsHolder != null && childsHolder.hasAncestorOrSelf(this)) return; // would make a cycle
		if(parent != null) parent.childs.remove(playerItem);
		parent = childsHolder;
		if(parent != null && !parent.childs.contains(playerItem))
			parent.childs.add(playerItem);
		linkVersion++;
	}
	
	/** Leaves the parent (the item keeps its current world position). */
	public void detach(){
		if(parent != null) parent.childs.remove(playerItem);
		if(parent != null) linkVersion++;
		parent = null;
	}
	
	public String getName(){
		return playerItem.getParentName();
	}
	public PlayerItem getPlayerItem(){
		return playerItem;
	}
	
	public void addChild(PlayerItem item){
		if(item == null || item == playerItem) return;
		item.getChildsHolder().setParent(this);
		if(!childs.contains(item) && item.getChildsHolder().parent == this) childs.add(item);
	}
	
	public ChildsHolder getChild(String child){
		for(PlayerItem item:childs){
			ChildsHolder holder=item.getChildsHolder();
			if(holder.getName().equals(child)){
				return holder;
			} else {
				ChildsHolder holder1=holder.getChild(child);
				if(holder1!=null) return holder1;
			}
		}
		return null;
	}
}
