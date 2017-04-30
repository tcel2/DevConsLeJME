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
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import com.github.devconslejme.es.DialogHierarchySystemI;
import com.github.devconslejme.es.HierarchyComp;
import com.github.devconslejme.es.HierarchyComp.EField;
import com.github.devconslejme.misc.Annotations.ToDo;
import com.github.devconslejme.misc.GlobalManagerI;
import com.github.devconslejme.misc.HierarchySorterI.EHierarchy;
import com.github.devconslejme.misc.JavaLangI;
import com.github.devconslejme.misc.MessagesI;
import com.github.devconslejme.misc.QueueI;
import com.github.devconslejme.misc.QueueI.CallableX;
import com.github.devconslejme.misc.QueueI.CallableXAnon;
import com.github.devconslejme.misc.jme.ColorI;
import com.github.devconslejme.misc.jme.IndicatorI;
import com.github.devconslejme.misc.jme.IndicatorI.GeomIndicator;
import com.github.devconslejme.misc.jme.MiscJmeI;
import com.github.devconslejme.misc.jme.SpatialHierarchyI;
import com.github.devconslejme.misc.jme.UserDataI;
import com.github.devconslejme.misc.jme.UserDataI.IUDKey;
import com.github.devconslejme.misc.lemur.DragParentestPanelListenerI;
import com.github.devconslejme.misc.lemur.MiscLemurI;
import com.github.devconslejme.misc.lemur.PopupHintHelpListenerI;
import com.github.devconslejme.misc.lemur.ResizablePanel;
import com.github.devconslejme.misc.lemur.ResizablePanel.IResizableListener;
import com.jme3.app.Application;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Sphere;
import com.simsilica.es.EntityId;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Command;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.ListBox;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.event.CursorButtonEvent;
import com.simsilica.lemur.event.CursorEventControl;
import com.simsilica.lemur.event.CursorMotionEvent;
import com.simsilica.lemur.event.DefaultCursorListener;

/**
* @author Henrique Abdalla <https://github.com/AquariusPower><https://sourceforge.net/u/teike/profile/>
*/
public class ContextMenuI implements IResizableListener{
	public static ContextMenuI i(){return GlobalManagerI.i().get(ContextMenuI.class);}
	
//	private Node	nodeParent;
	private ResizablePanel	rzpContextMenu;
	private String	strStyle;
	private Container	cntrContextOptions;
//	private Vector3f	v3fHierarchyParentInitialDisplacement;
	private EntityId	entid;
	private Label	lbl;
	private boolean	bShowDbgInfo = false;
	private boolean	bUseContextMenuAvailablePermanentIndicators=false;
	private GeomIndicator	giContextMenuAvailableIndicator;
//	private String	strUDKeyFollowContextMenuTarget = "FollowContextMenuTarget";
	
//	public static enum EUDKey implements IUDKey{
////		FollowContextMenuTarget(Spatial.class),
//		;
//		
//		private Class	cl;
//		EUDKey(Class cl){this.cl=cl;}
//		@Override	public Class getType() {return cl;}
//		
//		@Override
////		public String getUId(){return EUDKey.class.getName()+"/"+toString();}
//		public String getUId(){return JavaLangI.i().enumUId(this);}
//	}
	
	private Command<Button>	btncmd = new Command<Button>() {
		@Override
		public void execute(Button source) {
//			hideContextMenu();
			rzpContextMenu.close();
		}
	};
	private CallableX	cxDbgInfo = new CallableX() {
		@Override
		public Boolean call() {
			HierarchyComp hc = DialogHierarchyStateI.i().getHierarchyComp(rzpContextMenu);
			
			if(!rzpContextMenu.isOpened())return true;
			
			//after diag hierarchy is ready
			if(hc.getLastFocusTime()==-1)return true;
			
			String str = hc.toString().replace(",", "\n");
			PopupHintHelpListenerI.i().setPopupHintHelp(lbl, str);
			MessagesI.i().debugInfo(ContextMenuI.this, str);
			
			return true;
		}
	}.setDelaySeconds(1f).enableLoop();
	
	public static class ContextMenuAnon extends ContextMenu<ContextMenuAnon>{
		public ContextMenuAnon(ResizablePanel hrpParent) {
			super(hrpParent);
		}}
	/**
	 * use {@link ContextMenuAnon} for anonymous classes
	 */
	public static class ContextMenu<SELF extends ContextMenu<SELF>>{
		LinkedHashMap<String,ContextButton> hmContextOptions = new LinkedHashMap<String,ContextButton>();
		private Panel pnlSource;
		private ResizablePanel	hrpParent;
		private ContextButton	btnChoice;
		private boolean	bSingleChoiceMode;
		
		public ContextMenu(ResizablePanel	hrpParent){
			this.hrpParent=hrpParent;
		}
		
		/**
		 * to be set only when clicking from the listener here
		 * @param pnlSource
		 * @return 
		 */
		private SELF setContextSource(Panel pnlSource){
			this.pnlSource=pnlSource;
			return getThis();
		}
		
		public Panel getContextSource(){
			return pnlSource;
		}
		
		@SuppressWarnings("unused")
		@ToDo
		public ContextMenu createSubMenu(String strTextKey){
			ContextMenu cmSub = new ContextMenu(ContextMenuI.i().rzpContextMenu);
			
			addNewEntry(
				strTextKey, 
				new Command<Button>() {
					@Override
					public void execute(Button source) {
						ResizablePanel rzp = SpatialHierarchyI.i().getParentest(source, ResizablePanel.class, false);
	//					cmSub.setHierarchyParent(rzp);
						
						/*
						Vector3f v3f = source.getWorldTranslation().clone();
						v3f.x+=source.getSize().x;
						v3f.y-=source.getSize().y/2f;
						*/
						Vector2f v2f = GlobalManagerI.i().get(Application.class).getInputManager().getCursorPosition();
						
						ContextMenuI.i().showContextMenu(
							v2f, //MiscJmeI.i().toV2f(v3f),
							source.getText(), 
							cmSub.setContextSource(source));
					}
				},
				null
			);
			
			if(true)throw new UnsupportedOperationException(
				"TODO: this requires the context menu to not be limited to 1");
			
			return cmSub;
		}
		
		/**
		 * Each choice can have an UserData hint updater.
		 * That updater must set a hint help.
		 * 
		 * @param strTextKey
		 * @param cmd
		 * @param cxHintUpdater
		 * @return
		 */
		@SuppressWarnings("unchecked")
		public Button addNewEntry(String strTextKey, Command<Button> cmd, HintUpdater cxHintUpdater){
			ContextButton btn = new ContextButton(strTextKey);
			btn.addClickCommands(cmd);
//			if(cxHintUpdater!=null)UserDataI.i().setUserDataPSH(btn, EContext.HintUpdater, cxHintUpdater);
			btn.setHintUpdater(cxHintUpdater);
			hmContextOptions.put(strTextKey, btn);
			return btn;
		}

		public ResizablePanel getHierarchyParent() {
			return hrpParent;
		}

		public SELF setSingleChoice(ContextButton btnChoice) {
			this.btnChoice = btnChoice;
			return getThis();
		}

		public ContextButton getSingleChoice() {
			return btnChoice;
		}
		
		public SELF setSingleChoiceMode(boolean b){
			this.bSingleChoiceMode=b;
			return getThis();
		}
		
		/**
		 * this must be overriden by sub-classes
		 * @return
		 */
		@SuppressWarnings("unchecked")
		protected SELF getThis() {
			return (SELF) this;
		}

		public boolean isSingleChoiceMode() {
			return bSingleChoiceMode;
		}
		
//		public ContextMenu setHierarchyParent(ResizablePanel hrpParent) {
//			this.hrpParent=hrpParent;
//			return this;
//		}
	}
	
	/**
	 * keep private to keep this logic restricted to this class.
	 * To avoid creating usage confusion outside here.
	 */
	private static enum EContext implements IUDKey{
//		Choice(Button.class),
//		Menu(ContextMenu.class),
//		HintUpdater(CallableX.class),
		PopupHintHelp(String.class),
		;
		
		private Class	cl;
		private EContext(Class cl) {
			this.cl=cl;
		}
		
//		public String uId(){return EContext.class.getName()+"/"+toString();}
		@Override
		public String getUId(){return JavaLangI.i().enumUId(this);}
		
		@Override
		public Class getType() {
			return cl;
		}
	}
	
	public static abstract class HintUpdater extends CallableX<HintUpdater>{
		public void setPopupHintHelp(String str){
			putKeyValue(EContext.PopupHintHelp.getUId(),str);
//			this.setLoopEnabled(true).setPopupHintHelp(""); //tst dummy
		}

		public String getPopupHintHelp() {
			return getValue(EContext.PopupHintHelp.getUId());
		}
		
		@Override
		protected HintUpdater getThis() {
			return this;
		}
	}
	
	private static class ContextButton<SELF extends ContextButton<SELF>> extends Button{
		private HintUpdater	cxHintUpdater;

		public ContextButton(String s) {
			super(s);
		}
		
		public SELF setHintUpdater(HintUpdater cxHintUpdater) {
			this.cxHintUpdater = cxHintUpdater;
			return getThis();
		}

		/**
		 * sub-classes must override this!
		 * @return
		 */
		@SuppressWarnings("unchecked")
		protected SELF getThis() {
			return (SELF)this;
		}

		public HintUpdater getHintUpdater() {
			return cxHintUpdater;
		}
	}
	
//	private class ContextMenuListenerI extends DefaultCursorListener{
//		@Override
//		public void cursorExited(CursorMotionEvent event, Spatial target,				Spatial capture) {
//			super.cursorExited(event, target, capture);
//			//TODO not working well, see hideContextMenu(); 
//		}
//	}
	
	public static class ContextMenuOwnerListenerI extends DefaultCursorListener{
		public static ContextMenuOwnerListenerI i(){return GlobalManagerI.i().get(ContextMenuOwnerListenerI.class);}
		
		@Override
		protected void click(CursorButtonEvent event, Spatial target,				Spatial capture) {
			super.click(event, target, capture);
			
			if(event.getButtonIndex()!=1)return; //right mouse button
//			if(!Panel.class.isInstance(capture))return;
//			if(!Button.class.isInstance(capture))return;
			
			Button btn = (Button)capture;
			
			ContextMenu cm = UserDataI.i().getUserDataPSH(btn, ContextMenu.class);
			
			if(cm!=null){
				ContextMenuI.i().showContextMenu(event.getLocation(), btn.getText(), cm.setContextSource(btn));
				event.setConsumed();
			}
		}
		
		@Override
		public void cursorEntered(CursorMotionEvent event, Spatial target, Spatial capture) {
			super.cursorEntered(event, target, capture);
//			Spatial spt = target==null?capture:target;
			
//			System.out.println("Enter="+((Button)spt).getText());
//			if(target==null)return;
			
//			if(!ContextMenuI.i().giContextMenuAvailableIndicator.isEnabled()){
			ContextMenuI.i().giContextMenuAvailableIndicator.setEnabled(true).setTarget(target);
		}
		
		@Override
		public void cursorExited(CursorMotionEvent event, Spatial target,Spatial capture) {
			super.cursorExited(event, target, capture);
//			System.out.println("Exit="+((Button)target).getText());
			
			ContextMenuI.i().giContextMenuAvailableIndicator.setEnabled(false);
		}
	}
	
	public void configure(){//Node nodeParent) {
		strStyle = GuiGlobals.getInstance().getStyles().getDefaultStyle();
		
		rzpContextMenu = DialogHierarchyStateI.i().createDialog(ContextMenuI.class.getSimpleName(), strStyle);
		entid = DialogHierarchyStateI.i().getEntityId(rzpContextMenu); //DialogHierarchySystemI.i().createEntity(ContextMenuI.class.getSimpleName());
		
		rzpContextMenu.addResizableListener(this);
		
		DialogHierarchyStateI.i().getVisuals(rzpContextMenu).ignorePositionRelativeToParent();
		
		MiscJmeI.i().addToName(rzpContextMenu, ContextMenuI.class.getSimpleName(), true);
		
		DialogHierarchySystemI.i().setHierarchyComp(entid, 
			EField.eHierarchyType, EHierarchy.Top,
			EField.bVolatileModal, true
		);
		
		rzpContextMenu.setAllEdgesEnabled(false); //it is here for the hierarchy (not the resizing)
		
		cntrContextOptions = new Container(strStyle);
		rzpContextMenu.setContents(cntrContextOptions);
		rzpContextMenu.setBackground(new QuadBackgroundComponent(ColorRGBA.Cyan));
		
		lbl = new Label("");
		
//		CursorEventControl.addListenersToSpatial(rzpContextMenu, new ContextMenuListenerI());
		
		DialogHierarchyStateI.i().addRequestAutoFocus(rzpContextMenu);
		
		if(bShowDbgInfo)QueueI.i().enqueue(cxDbgInfo);
		
		giContextMenuAvailableIndicator = IndicatorI.i().createIndicator(ColorI.i().colorChangeCopy(ColorRGBA.Cyan,0f,0.75f));
	}
	
	public void applyContextMenuAtListBoxItems(ListBox lstbx, ContextMenu cm){
//		ArrayList<Panel> apnl = MiscJmeI.i().getAllChildrenRecursiveFrom(lstbx.getGridPanel(), Panel.class, null);
		ArrayList<Panel> apnl = MiscLemurI.i().getAllListBoxItems(lstbx,false);
		for(Panel pnl:apnl){
			applyContextMenuAt(pnl, cm);
		}
	}
	
	private void permanentIndicator(Spatial spt){
		if (spt instanceof Node) {
			Node node = (Node) spt;
			QueueI.i().enqueue(new CallableX() {
				@Override
				public Boolean call() {
					BoundingBox bb = (BoundingBox)node.getWorldBound();
					float fRadius=bb.getYExtent();
					if(fRadius==0f)return false;
					
					Geometry geom = new Geometry("ContextMenuAvailableIndicator", new Sphere(4, 7, fRadius));
					geom.setLocalTranslation(
						fRadius,//(bb.getXExtent()*2f)-fRadius, 
						-bb.getYExtent(), 
						bb.getZExtent()*2f
					);
					geom.setMaterial(ColorI.i().retrieveMaterialUnshadedColor(
						ColorI.i().colorChangeCopy(ColorRGBA.Green, 0f, 0.15f)));
					node.attachChild(geom);
					
					return true;
				}
			});
		}
	}
	
	/**
	 * {@link Spatial} N -> 1 {@link ContextMenu}
	 * @param spt
	 * @param cm
	 */
	public void applyContextMenuAt(Spatial spt, ContextMenu cm){
		UserDataI.i().setUserDataPSH(spt, cm);
		CursorEventControl.addListenersToSpatial(spt,ContextMenuOwnerListenerI.i());
		
		if(bUseContextMenuAvailablePermanentIndicators){
			permanentIndicator(spt);
		}
		
	}
	
	/**
	 * @param v2fMouseCursorPos
	 * @param strContextMenuTitle
	 * @param cm
	 */
	@SuppressWarnings("unchecked")
	private void showContextMenu(Vector2f v2fMouseCursorPos, String strContextMenuTitle, ContextMenu<?> cm) {
		cntrContextOptions.clearChildren();
		
		int i=0;
		lbl.setText("Context:"+strContextMenuTitle);
		DragParentestPanelListenerI.i().applyAt(lbl);
		cntrContextOptions.addChild(lbl, i++, 0);
		for(Entry<String, ContextButton> entry : (cm.hmContextOptions).entrySet()){
			ContextButton btnChoice = entry.getValue();
			
			btnChoice.addClickCommands(btncmd);
			
			// popup hint help
			PopupHintHelpListenerI.i().resetPopupHelp(btnChoice); //clear
			HintUpdater cxHU = btnChoice.getHintUpdater();
			if(cxHU!=null){
				cxHU.setPopupHintHelp(null);
				if(cxHU.call()){
					if(cm.isSingleChoiceMode()){
						cm.setSingleChoice(btnChoice);
						GeomIndicator gi = IndicatorI.i().createIndicator(ColorRGBA.Yellow);
						gi.setTarget(btnChoice).setEnabled(true);
					}
					
					PopupHintHelpListenerI.i().setPopupHintHelp(btnChoice, cxHU.getPopupHintHelp());
				}
			}
			
			cntrContextOptions.addChild(btnChoice, i++, 0);
		}
		
		DialogHierarchyStateI.i().showDialogAsModal(cm.getHierarchyParent(), rzpContextMenu);
		
		rzpContextMenu.setPreferredSize(
			new Vector3f(
				200, 
				30*cm.hmContextOptions.size(), 
				rzpContextMenu.getPreferredSize().z)
		);
		
		int iDisplacement=20;
		rzpContextMenu.setLocalTranslation(
			v2fMouseCursorPos.getX()-iDisplacement, 
			v2fMouseCursorPos.getY()+iDisplacement, 
			0); // z will be fixed by diag hierarchy
	}
	
	public boolean isTheContextMenu(ResizablePanel hs) {
		return (hs==rzpContextMenu);
	}


	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ContextMenuI [rzp=");
		builder.append(rzpContextMenu);
		builder.append(", strStyle=");
		builder.append(strStyle);
		builder.append(", cntr=");
		builder.append(cntrContextOptions);
		builder.append(", entid=");
		builder.append(entid);
		builder.append(", lbl=");
		builder.append(lbl);
		builder.append(", bShowDbgInfo=");
		builder.append(bShowDbgInfo);
		builder.append(", btncmd=");
		builder.append(btncmd);
		builder.append(", cxDbgInfo=");
		builder.append(cxDbgInfo);
		builder.append("]");
		return builder.toString();
	}
	
	public String getReport(){
		return toString()+","+DialogHierarchyStateI.i().getHierarchyComp(rzpContextMenu).toString();
	}


	public boolean isUseContextMenuAvailableIndicators() {
		return bUseContextMenuAvailablePermanentIndicators;
	}


	public void setUseContextMenuAvailableIndicators(boolean bUseContextMenuIndicators) {
		this.bUseContextMenuAvailablePermanentIndicators = bUseContextMenuIndicators;
	}

//	public void hideContextMenu() {
//		rzpContextMenu.removeFromParent();
//		IndicatorI.i().disableAllIndicatorsRecursively(rzpContextMenu);
//	}
	
	@Override	public void resizerUpdatedLogicalStateEvent(float tpf,			ResizablePanel rzpSource) {	}
	@Override
	public void removedFromParentEvent(ResizablePanel rzpSource) {
		IndicatorI.i().destroyAllIndicatorsRecursively(rzpSource);
//		hideContextMenu();
	}
	@Override	public void resizedEvent(ResizablePanel rzpSource, Vector3f v3fNewSize) {	}
	@Override	public void endedResizingEvent(ResizablePanel rzpSource) {	}
}
