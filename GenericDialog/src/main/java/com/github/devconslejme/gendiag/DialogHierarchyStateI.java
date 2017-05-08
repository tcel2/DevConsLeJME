/* 
Copyright (c) 2017, Henrique Abdalla <https://github.com/AquariusPower><https://sourceforge.net/u/teike/profile/>

All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted 
provided that the following conditions are met:

1.	Redistributions of source code must retain the above copyright notice, this list of conditions 
	and the following disclaimer.

2.	Redistributions in binary form must reproduce the above copyright notice, this list of conditions 
	and the following disclaimer in the documentation and/or other materials provided with the distribution.

3.	Neither the name of the copyright holder nor the names of its contributors may be used to endorse 
	or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A 
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR 
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN 
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.github.devconslejme.gendiag;

import java.util.ArrayList;

import com.github.devconslejme.es.DialogHierarchySystemI;
import com.github.devconslejme.es.DialogHierarchyComp;
import com.github.devconslejme.es.DialogHierarchyComp.DiagCompBean;
import com.github.devconslejme.misc.DetailedException;
import com.github.devconslejme.misc.GlobalManagerI;
import com.github.devconslejme.misc.HierarchySorterI.EHierarchyType;
import com.github.devconslejme.misc.MessagesI;
import com.github.devconslejme.misc.QueueI;
import com.github.devconslejme.misc.QueueI.CallableX;
import com.github.devconslejme.misc.QueueI.CallableXAnon;
import com.github.devconslejme.misc.jme.ColorI;
import com.github.devconslejme.misc.jme.EffectArrow;
import com.github.devconslejme.misc.jme.EffectElectricity;
import com.github.devconslejme.misc.jme.EffectManagerStateI;
import com.github.devconslejme.misc.jme.IEffect;
import com.github.devconslejme.misc.jme.MiscJmeI;
import com.github.devconslejme.misc.jme.SpatialHierarchyI;
import com.github.devconslejme.misc.jme.UserDataI;
import com.github.devconslejme.misc.lemur.CursorListenerX;
import com.github.devconslejme.misc.lemur.DragParentestPanelListenerI;
import com.github.devconslejme.misc.lemur.HoverHighlightEffectI;
import com.github.devconslejme.misc.lemur.MiscLemurI;
import com.github.devconslejme.misc.lemur.ResizablePanel;
import com.github.devconslejme.misc.lemur.ResizablePanel.IResizableListener;
import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityId;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Command;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.core.VersionedReference;
import com.simsilica.lemur.event.CursorButtonEvent;
import com.simsilica.lemur.event.CursorEventControl;
import com.simsilica.lemur.focus.FocusManagerState;


/**
 * @author Henrique Abdalla <https://github.com/AquariusPower><https://sourceforge.net/u/teike/profile/>
 */
public class DialogHierarchyStateI extends AbstractAppState implements IResizableListener{
	public static DialogHierarchyStateI i(){return GlobalManagerI.i().get(DialogHierarchyStateI.class);}
	
	private Application	app;
	/** dialogs may close, but not be discarded TODO confirm this:  */
	private ArrayList<ResizablePanel> arzpAllCreatedDialogs = new ArrayList<ResizablePanel>();
	private float	fBeginOrderPosZ;
	private Node	nodeToMonitor;
	private FocusManagerState	focusState;
	private IEffect	ieffParentToChildLink = new EffectArrow();
	private IEffect	ieffLinkedDragEffect = new EffectElectricity().setColor(ColorRGBA.Cyan);//ColorI.i().colorChangeCopy(ColorRGBA.Blue, 0f, 0.5f));
	private BlockerCursorListenerX blockerListener = new BlockerCursorListenerX();
	private ColorRGBA	colorBlocker = ColorI.i().colorChangeCopy(ColorRGBA.Red, 0f, 0.15f);
	private DialogHierarchySystemI sys = DialogHierarchySystemI.i();
	private ResizablePanel	rzpCurrentlyBeingResized;
	/** so the blocker can stay in that gap */
	private float	fInBetweenGapDistZ=1.0f;
	private float	fMinLemurPanelSizeZ = 0.01f; //TODO collect this value dinamically from lemur in some way
	private ColorRGBA	colorInvisible = new ColorRGBA(0,0,0,0);
	protected float	fCurrentOrderPosZ;
	private CallableX cxZOrder = new CallableX(){
		@Override
		public Boolean call() {
			fCurrentOrderPosZ = fBeginOrderPosZ;
			for(Entity ent:sys.getSortedHierarchyDialogs()){
				updateZOrder(ent.getId());
			}
			return true;
		}
	}.enableLoop().setDelaySeconds(1f);
	private CallableX	cxAutoFocus = new CallableX() { //TODO this delay still has a chance of typing something at other input field? like when holding for long a key?
		@Override
		public Boolean call() {
			for(Panel pnl:apnlAutoFocus){
				ResizablePanel rzp = SpatialHierarchyI.i().getParentest(pnl, ResizablePanel.class, true);
				DialogHierarchyComp hc = DialogHierarchyStateI.i().getHierarchyComp(rzp);
				if(!hc.isOpened())continue;
				
				if(!hc.isBlocked()){
					GuiGlobals.getInstance().requestFocus(pnl);
					return true; //skip others, they cant fight against each other...
				}
			}
			
			return true;
		}
	}.setName("FocusAtDevConsInput").setDelaySeconds(0.25f).enableLoop();
	private ArrayList<Panel>	apnlAutoFocus = new ArrayList<Panel>();
	private VersionedReference<Integer>	vriResizableBorderSize;
	private boolean	bRequestRetryZOrder;
	private boolean	bLogZOrderDebugInfo;
//	protected ResizablePanel	minimizedDiags;
//	protected Container	cntrMinimized;
	
	public static class BlockerCursorListenerX extends CursorListenerX{
		@Override
		protected boolean click(CursorButtonEvent event, Spatial target, 	Spatial capture) {
			/**
			 * all buttons will be accepted to raise the dialog
			 */
//			super.click(event, target, capture);
			
			Visuals vs = DialogHierarchyStateI.i().getVisuals(capture);
			DialogHierarchyComp hc = DialogHierarchySystemI.i().getHierarchyComp(vs.getEntityId());
			if(hc.isBlocked()){
				DialogHierarchyStateI.i().setFocusRecursively(vs.getEntityId());
//				return true;
			}
			
			return false; //do not consume this click, it is just a focus raiser, the blocker may have some functionality one day..
		}
	}
	
	/**
	 * keep setters private
	 */
	public static class Visuals{
		private EntityId entid;
		private ResizablePanel rzpDiag;
		private ResizablePanel pnlBlocker;
		
		/** only one effect per child, but many per parent */
		private IEffect ieffLinkToParent;
		
		private boolean bAllowPositionRelativeToParent=true;
		private Vector3f	v3fPositionRelativeToParent = new Vector3f(20, -20, 0); //cascade like
//		@Override
//		public boolean equals(Object obj) {
//			if(obj==rzpDiag)return true;
//			if(obj==pnlBlocker)return true;
//			if(obj==ieff)return true;
//			if((Long)obj==entid.getId())return true;
//			return false;
//		}
		public EntityId getEntityId() {
			return entid;
		}
		private void setEntityId(EntityId entid) {
			this.entid = entid;
		}
		
		public ResizablePanel getDialog() {
			return rzpDiag;
		}
		private void setDiag(ResizablePanel rzpDiag) {
			this.rzpDiag = rzpDiag;
		}
		
		public ResizablePanel getBlocker() {
			return pnlBlocker;
		}
		private void setBlocker(ResizablePanel pnlBlocker) {
			this.pnlBlocker = pnlBlocker;
		}
		
		public Vector3f getPositionRelativeToParent() {
			return v3fPositionRelativeToParent;
		}
		private void setPositionRelativeToParent(Vector3f v3fPositionRelativeToParent) {
//			if(!isAllowPositionRelativeToParent())return;
			this.v3fPositionRelativeToParent = v3fPositionRelativeToParent;
		}
		
		public IEffect getEffLinkToParent() {
			return ieffLinkToParent;
		}
		private void setEffLinkToParent(IEffect ieffLinkToParent) {
			this.ieffLinkToParent = ieffLinkToParent;
		}
		
		public boolean isAllowPositionRelativeToParent() {
			return bAllowPositionRelativeToParent;
		}
		/**
		 * will prevent it from being updated by user input
		 */
		public void ignorePositionRelativeToParent() {
			this.bAllowPositionRelativeToParent = false;
		}
		
	}

	public void configure(Node nodeToMonitor,float fBeginOrderZ){
		this.fBeginOrderPosZ=fBeginOrderZ;
		this.nodeToMonitor=nodeToMonitor;
		
		app=GlobalManagerI.i().get(Application.class);
    app.getStateManager().attach(this);
		focusState=app.getStateManager().getState(FocusManagerState.class);
		
		vriResizableBorderSize = ResizablePanel.getResizableBorderSizeDefaultVersionedReference();
    
		EffectManagerStateI.i().add(ieffLinkedDragEffect);
		
		sys.configure();
//		ed=DialogHierarchySystemI.i().getEntityData();
		
		QueueI.i().enqueue(cxAutoFocus);
		
//		QueueI.i().enqueue(new CallableXAnon() {
//			@Override
//			public Boolean call() {
//				minimizedDiags = createDialog("Minimized dialogs panel", null);
//				cntrMinimized=new Container();
//				minimizedDiags.setContents(cntrMinimized);
//				sys.setHierarchyComp(getEntityId(minimizedDiags), 
//					new CompBean().setHierarchyType(EHierarchyType.Top));
//				return true;
//			}
//		});
	}
	
//	private HashBiMap<Long,Visuals> hmDiag = HashBiMap.create();
	
	public ResizablePanel createDialog(String strName, String strStyle) {
		EntityId entid = sys.createEntity(strName);
		
		// main dialog panel
		ResizablePanel rzp = new ResizablePanel(strStyle);
		MiscJmeI.i().addToName(rzp, strName, true);
		rzp.addResizableListener(this);
		HoverHighlightEffectI.i().applyAt(rzp, (QuadBackgroundComponent)rzp.getResizableBorder());
		
		// blocker
		ResizablePanel pnlBlocker = new ResizablePanel(strStyle);
		MiscJmeI.i().addToName(pnlBlocker, strName+"_Blocker", true);
//		pnlBlocker.setAllEdgesEnabled(false);
//		pnlBlocker.setBackground(new QuadBackgroundComponent(colorBlocker));
		pnlBlocker.setResizableBorder(new QuadBackgroundComponent(colorBlocker));
		pnlBlocker.setBackground(null);
		DragParentestPanelListenerI.i().applyAt(pnlBlocker, rzp); // the blocker has not a parent panel! so it will let the dialog be dragged directly!  
		DragParentestPanelListenerI.i().applyAt(rzp); // the resizable border can be used to move/drag the parentest with the middle mouse button!  
		CursorEventControl.addListenersToSpatial(pnlBlocker,blockerListener);
		
		// visual data
		Visuals vs = new Visuals();
		vs.setEntityId(entid);
		vs.setDiag(rzp);
		vs.setBlocker(pnlBlocker);
		UserDataI.i().setUserDataPSHSafely(rzp, vs);
		UserDataI.i().setUserDataPSHSafely(pnlBlocker, vs);
		
		arzpAllCreatedDialogs.add(rzp);
		
		return rzp;
	}
	
	/**
	 * the blocker and the dialog share the same object!
	 * @param spt
	 * @return
	 */
	public Visuals getVisuals(Spatial spt){
		if(spt==null)return null;
		return UserDataI.i().getUserDataPSH(spt,Visuals.class);
	}
	
	public DialogHierarchyComp getHierarchyComp(Spatial spt){
		Visuals vs = getVisuals(spt);
		if(vs==null)return null;
		return sys.getHierarchyComp(vs.getEntityId());
	}
	
	public void showDialog(ResizablePanel rzp) {
//		if(rzp.getLocalTranslation().length()==0){ //not set, center it
//			MiscLemurI.i().moveToScreenCenterXY(rzp);
//		}
		
//		if(rzp.getPreferredSize().length()==0){ //not set, use default
//			rzp.setPreferredSize(((BoundingBox)rzp.getWorldBound()).getExtent(null).mult(2f));
//		}
		
		nodeToMonitor.attachChild(rzp);
		
		EntityId entid = getVisuals(rzp).getEntityId();
		
		if(sys.getHierarchyComp(entid).getLastFocusTime()==-1){ //1st time only
			CursorListenerX clSimpleFocusRaiser = new CursorListenerX(){
				@Override
				protected boolean click(CursorButtonEvent event, Spatial target, Spatial capture) {
					setFocusRecursively(entid);
					return false; //never consume this click!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
				}
			};
			
			for(Panel pnl:SpatialHierarchyI.i().getAllChildrenRecursiveFrom(rzp, Panel.class, null)){
				CursorEventControl.addListenersToSpatial(pnl,clSimpleFocusRaiser);
			}
		}
		
		sys.setHierarchyComp(entid,new DiagCompBean()
			.setOpened(true)
			.setLastFocusTime(app.getTimer().getTime())
		);
		
		setFocusRecursively(entid);
	}

	public void showDialogAsModeless(ResizablePanel rzpParent, ResizablePanel rzpChild) {
		showDialogAs(false, rzpParent, rzpChild);
	}
	public void showDialogAsModal(ResizablePanel rzpParent, ResizablePanel rzpChild) {
		showDialogAs(true, rzpParent, rzpChild);
	}
	private void showDialogAs(boolean bModal, ResizablePanel rzpParent, ResizablePanel rzpChild) {
		if(!rzpParent.isOpened())throw new DetailedException("parent not open",rzpParent,rzpChild);
		
		sys.setHierarchyComp(getVisuals(rzpChild).getEntityId(),new DiagCompBean()
			.setHierarchyParent(getVisuals(rzpParent).getEntityId())
			.setHierarchyModal(bModal)
		);
		
		showDialog(rzpChild);
		
		applyParentChildLinkEffect(rzpParent, rzpChild);
	}
	
	protected void applyParentChildLinkEffect(ResizablePanel rzpParent, ResizablePanel rzpChild) {
		if(!getHierarchyComp(rzpParent).isShowLinksFromChilds())return;
		
		Visuals vsChild = getVisuals(rzpChild);
		if(vsChild.getEffLinkToParent()==null || vsChild.getEffLinkToParent().isDiscarded()){
			IEffect effLink = ieffParentToChildLink.clone();
			
			effLink
				.setFollowFromTarget(rzpParent, null)
				.setFollowToTarget(rzpChild, null)
				.setUseFollowToPosZ() //same Z depth of child that is bove parent
				.setPlay(true)
				;
			
			EffectManagerStateI.i().add(effLink);
			
			vsChild.setEffLinkToParent(effLink);
		}
	}
	
	@Override
	public void update(float tpf) {
		super.update(tpf);
		
		sys.update(tpf, app.getTimer().getTime());
		
		updateDialogsResizableBorderSize();
		
		updateDragLinkToParentEffect();
		
		updateVolatileModalAutoClose();
		
		if(bRequestRetryZOrder){
			QueueI.i().enqueue(cxZOrder);
			bRequestRetryZOrder=false;
		}
		
//		int iMinChildren = cntrMinimized.getLayout().getChildren().size();
//		if(minimizedDiags.getParent()==null){
//			if(iMinChildren>0)nodeToMonitor.attachChild(minimizedDiags);
//		}else{
//			if(iMinChildren==0)minimizedDiags.removeFromParent();
//		}
	}
	
	/**
	 * even the not currently opened ones
	 */
	private void updateDialogsResizableBorderSize() {
		if(vriResizableBorderSize.update()){
			for(ResizablePanel rzp:arzpAllCreatedDialogs){
				rzp.setResizableBorderSize(vriResizableBorderSize.get(), vriResizableBorderSize.get());
			}
		}
	}

	private void updateVolatileModalAutoClose() {
		Spatial spt = focusState.getFocus();
		if(spt==null)return;
		
		ResizablePanel rzp = SpatialHierarchyI.i().getParentest(spt, ResizablePanel.class, true);
		DialogHierarchyComp hc = getHierarchyComp(rzp);
		if(hc==null)return;
		
		if(!hc.isVolatileModal())return;
		
		if(hc.isBlocked())return; //it can have an active child
		
		if(!rzp.isUpdateLogicalStateSuccess())return; //as 1st time it may not be ready yet
		
		if(DragParentestPanelListenerI.i().getParentestBeingDragged()==rzp)return; //wait dragging end...
		
		if(!MiscLemurI.i().isMouseCursorOver(rzp)){
			rzp.close();
		}
	}

	private void updateDragLinkToParentEffect(){
		Panel pnl = DragParentestPanelListenerI.i().getParentestBeingDragged();
		DialogHierarchyComp hc = getHierarchyComp(pnl);
		if(hc!=null){
			if(!ieffLinkedDragEffect.isPlaying()){ //this also identifies the start of the dragging!
				EntityId entidParent = hc.getHierarchyParent();
				if(entidParent!=null){
					ResizablePanel rzpParent = getOpenDialog(entidParent);
					ieffLinkedDragEffect
						.setOwner(pnl)
						.setFollowFromTarget(rzpParent, null)
						.setFollowToTarget(pnl, null)
						.setUseFollowToPosZ()
						.setPlay(true)
						;
				}
				
				setFocusRecursively(getEntityId(pnl));
				
				Visuals vs = getVisuals(pnl);
				IEffect ef = vs.getEffLinkToParent();
				if(ef!=null)ef.setPlay(false); // suspend the hierarchy link effect to unclutter while dragging
				
				QueueI.i().enqueue(new CallableXAnon() {
					@Override
					public Boolean call() {
						if(DragParentestPanelListenerI.i().getParentestBeingDragged()!=null)return false;
						if(ef!=null)ef.setPlay(true); //restore the hierarchy link effect
						return true;
					}
				});
			}
			
		}else{
			if(ieffLinkedDragEffect.isPlaying()){
				ieffLinkedDragEffect.setPlay(false);
//				getVisuals(pnl).getEffLinkToParent().setPlay(true);
			}
		}
	}
	
	public ResizablePanel getOpenDialog(EntityId entid){
		for(Spatial spt:nodeToMonitor.getChildren()){
			Visuals vs = getVisuals(spt);
			if(vs==null)continue;
			if(vs.getEntityId().equals(entid))return vs.getDialog();
		}
		return null;
	}
	
	/**
	 * 
	 * @param tpf ignored if null
	 * @param entid
	 * @param rzp
	 */
//	public void updateBlocker(Float tpf,EntityId entid, ResizablePanel rzp){
	public void updateBlocker(Float tpf,Visuals vs){
		// blocker work
		ResizablePanel pnlBlocker = vs.getBlocker();
		if(vs.getDialog().isOpened()){
			if(pnlBlocker.getParent()==null){
				nodeToMonitor.attachChild(pnlBlocker);
			}
			
			// how many childs are modal
			int iModalCount=0;
			for(Entity entChild:sys.getAllOpenedDialogs(vs.getEntityId())){
				if(entChild.get(DialogHierarchyComp.class).isHierarchyModal())iModalCount++;
			}
			sys.enableBlockingLayer(vs.getEntityId(),iModalCount>0);
			
			// z order
			Vector3f v3fSize = MiscJmeI.i().getBoundingBoxSizeCopy(vs.getDialog());
			if(v3fSize!=null){
				if(Float.compare(v3fSize.length(),0f)!=0){ //waiting top panel be updated by lemur
					Vector3f v3fPos = vs.getDialog().getLocalTranslation().clone();
					
//					QuadBackgroundComponent qbc = ((QuadBackgroundComponent)pnlBlocker.getBackground());
					QuadBackgroundComponent qbc = (QuadBackgroundComponent)pnlBlocker.getResizableBorder();
					if(sys.isBlocked(vs.getEntityId())){
						v3fPos.z += v3fSize.z + fInBetweenGapDistZ/2f; //above
						qbc.setColor(colorBlocker);
					}else{
						v3fPos.z -= fInBetweenGapDistZ/2f; //below to not receive mouse/cursor events
						qbc.setColor(colorInvisible);
					}
					
					pnlBlocker.setLocalTranslationXY(v3fPos).setLocalTranslationZ(v3fPos.z);
					
//					Vector3f v3fBlockerSize = v3fSize.clone();
//					v3fBlockerSize.z=fMinLemurPanelSizeZ;
//					pnlBlocker.setPreferredSizeWH(v3fBlockerSize);//rzp.getPreferredSize());
					pnlBlocker.setPreferredSizeWH(v3fSize);
				}
			}
			
		}else{
			if(pnlBlocker.getParent()!=null){
				pnlBlocker.removeFromParent();
			}
		}
	}
	
	/**
	 * see {@link DialogHierarchySystemI#getAllOpenedDialogs(EntityId)}
	 * @return
	 */
	public ResizablePanel[] getAllOpenedDialogs() {
		ArrayList<ResizablePanel> arzp = new ArrayList<ResizablePanel>();
		for(Entity ent:sys.getAllOpenedDialogs(null)){
			arzp.add(getOpenDialog(ent.getId()));
		}
		return arzp.toArray(new ResizablePanel[0]);
	}
	
	@Override
	public void resizableUpdatedLogicalStateEvent(float tpf, ResizablePanel rzpSource) {
		Visuals vs = getVisuals(rzpSource);
		updateBlocker(tpf, vs);
		updateDragResizeRelativeParentPos(tpf, rzpSource, vs);
	}
	
	private void updateDragResizeRelativeParentPos(float tpf, ResizablePanel rzpSource, Visuals vs) {
		if(!vs.isAllowPositionRelativeToParent())return;
		
		DialogHierarchyComp hc = getHierarchyComp(rzpSource);
		EntityId entidParent = hc.getHierarchyParent();
		if(entidParent!=null){
			if(vs.getPositionRelativeToParent()!=null){
				ResizablePanel rzpParent = getOpenDialog(entidParent);
				
				if(
						DragParentestPanelListenerI.i().getParentestBeingDragged()==rzpSource
						||
						getCurrentlyBeingResized()==rzpSource
				){
					/**
					 * update displacement
					 */
					vs.setPositionRelativeToParent(
						rzpSource.getLocalTranslation().subtract(rzpParent.getLocalTranslation())
					);
				}else{
					/**
					 * set position relatively to parent
					 */
					Vector3f v3fNewPos = rzpParent.getLocalTranslation().clone();
					v3fNewPos.addLocal(vs.getPositionRelativeToParent());
					rzpSource.setLocalTranslationXY(v3fNewPos);
				}
			}
		}
	}

	public ResizablePanel getCurrentlyBeingResized(){
		return rzpCurrentlyBeingResized;
	}

	@Override
	public void resizableStillResizingEvent(ResizablePanel rzpSource, Vector3f v3fNewSize) {
		if(rzpCurrentlyBeingResized==null){ //marks it's start!
			setFocusRecursively(getEntityId(rzpSource));
		}
		
		rzpCurrentlyBeingResized=rzpSource;
	}

	@Override
	public void resizableEndedResizingEvent(ResizablePanel rzpSource) {
		rzpCurrentlyBeingResized=null; //user can resize only one at a time 
	}

	@Override
	public void resizableRemovedFromParentEvent(ResizablePanel rzpSource) {
//		updateBlocker(null, getVisuals(rzpSource).getEntityId(), rzpSource);
		Visuals vs = getVisuals(rzpSource);
		
		updateBlocker(null, vs);
		
		sys.setHierarchyComp(vs.getEntityId(), new DiagCompBean().setOpened(false));
		
		if(vs.getEffLinkToParent()!=null)vs.getEffLinkToParent().setAsDiscarded();
	}
	
	private void updateZOrder(EntityId entid){
		ResizablePanel rzp = getOpenDialog(entid);
		
		if(!rzp.isUpdateLogicalStateSuccess())bRequestRetryZOrder=true;
		
		rzp.setLocalTranslationZ(fCurrentOrderPosZ);
		sys.setHierarchyComp(entid, new DiagCompBean().setDialogZ(fCurrentOrderPosZ));
		
		// prepare next
		BoundingBox bb = (BoundingBox)getOpenDialog(entid).getWorldBound();
		if(bb!=null){ //only if it is ready
			float fHeight = bb.getZExtent()*2f;
			if(isLogZOrderDebugInfo()){
				MessagesI.i().debugInfo(this, "DiagHierarchyZOrder:"+rzp.getName()+","+entid+","+fCurrentOrderPosZ+","+fHeight);
			}
			
			sys.setHierarchyComp(entid, new DiagCompBean().setBoundingHeightZ(fHeight));
			
			// now updates it
			fCurrentOrderPosZ += fHeight +fInBetweenGapDistZ;
		}
		
	}
	
	/**
	 * for the entire current tree
	 * @param entid
	 */
	public void setFocusRecursively(EntityId entid){
		setFocus(sys.getParentest(entid), true);
		
		// one second time priorizing the specific hierarchy sub-tree
		for(EntityId entidP:sys.getParentList(entid)){
			setFocus(entidP, false);
		}
		
		QueueI.i().enqueue(cxZOrder);
	}
	private void setFocus(EntityId entid, boolean bRecursive){
		ResizablePanel rzp = getOpenDialog(entid);
		GuiGlobals.getInstance().requestFocus(rzp);
		sys.updateLastFocusAppTimeNano(entid, app.getTimer().getTime());
		
		if(bRecursive){
			for(Entity ent:sys.getAllOpenedDialogs(entid)){
				setFocus(ent.getId(),true);
			}
		}
	}

	public EntityId getEntityId(Spatial spt) {
		return getVisuals(spt).getEntityId();
	}

	public float getInBetweenGapDistZ() {
		return fInBetweenGapDistZ;
	}

	public void setInBetweenGapDistZ(float fInBetweenGapDistZ) {
		this.fInBetweenGapDistZ = fInBetweenGapDistZ;
		if(this.fInBetweenGapDistZ<1f)this.fInBetweenGapDistZ=1f;
	}
	
	
	public void addRequestAutoFocus(Panel pnl) {
		if(!apnlAutoFocus.contains(pnl))apnlAutoFocus.add(pnl);
	}

	public boolean isLogZOrderDebugInfo() {
		return bLogZOrderDebugInfo;
	}

	public void setLogZOrderDebugInfo(boolean bLogZOrderDebugInfo) {
		this.bLogZOrderDebugInfo = bLogZOrderDebugInfo;
	}

//	@SuppressWarnings("unchecked")
//	public void minimize(AbstractGenericDialog sgd) {
//		if(getHierarchyComp(sgd.getDialog()).getHierarchyParent()!=null)return;
//		
//		Button btn = new Button(sgd.getTitle());
//		cntrMinimized.addChild(btn, cntrMinimized.getLayout().getChildren().size());
//		btn.addClickCommands(new Command<Button>() {
//			@Override
//			public void execute(Button source) {
//				showDialog(sgd.getDialog());
//				btn.removeFromParent();
//				
//				//readd remaining
//				ArrayList<Node> children = new ArrayList<Node>(cntrMinimized.getLayout().getChildren()); //remaining
//				cntrMinimized.getLayout().clearChildren();
////				for(int i=0;i<children.size();i++){
//				int i=0;for(Node child:children){
//					cntrMinimized.addChild(child, i++);
//				}
//			}
//		});
//		sgd.close();
//	}

//	public String getReport(ResizablePanel rzp) {
//		String str="";
//		return getHierarchyComp(rzp).toString();
//		return str;
//	}

}
