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

import com.github.devconslejme.es.DialogHierarchyComp;
import com.github.devconslejme.es.DialogHierarchyComp.DiagCompBean;
import com.github.devconslejme.es.DialogHierarchySystemI;
import com.github.devconslejme.gendiag.ContextMenuI.ContextMenu.ApplyContextChoiceCmd;
import com.github.devconslejme.gendiag.DialogHierarchyStateI.DialogVisuals;
import com.github.devconslejme.misc.DetailedException;
import com.github.devconslejme.misc.GlobalManagerI;
import com.github.devconslejme.misc.HierarchySorterI.EHierarchyType;
import com.github.devconslejme.misc.MessagesI;
import com.github.devconslejme.misc.QueueI;
import com.github.devconslejme.misc.QueueI.CallableX;
import com.github.devconslejme.misc.QueueI.CallableXAnon;
import com.github.devconslejme.misc.StringI.EStringMatchMode;
import com.github.devconslejme.misc.jme.ColorI;
import com.github.devconslejme.misc.jme.IndicatorI;
import com.github.devconslejme.misc.jme.IndicatorI.GeomIndicator;
import com.github.devconslejme.misc.jme.MiscJmeI;
import com.github.devconslejme.misc.jme.UserDataI;
import com.github.devconslejme.misc.lemur.AbsorbClickCommandsI;
import com.github.devconslejme.misc.lemur.CursorListenerX;
import com.github.devconslejme.misc.lemur.DragParentestPanelListenerI;
import com.github.devconslejme.misc.lemur.MiscLemurI;
import com.github.devconslejme.misc.lemur.PopupHintHelpListenerI;
import com.github.devconslejme.misc.lemur.ResizablePanel;
import com.github.devconslejme.misc.lemur.ResizablePanel.IResizableListener;
import com.github.devconslejme.misc.lemur.SizeAndLocationI;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Sphere;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Command;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.ListBox;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.event.CursorButtonEvent;
import com.simsilica.lemur.event.CursorEventControl;
import com.simsilica.lemur.event.CursorMotionEvent;

/**
* @author Henrique Abdalla <https://github.com/AquariusPower><https://sourceforge.net/u/teike/profile/>
*/
public class ContextMenuI implements IResizableListener{
	public static ContextMenuI i(){return GlobalManagerI.i().get(ContextMenuI.class);}
	
	private boolean	bUseContextMenuAvailablePermanentIndicators=false;
	private GeomIndicator	giContextMenuAvailableIndicator;
	private boolean	bShowDbgInfo = false;
	
	public static class ContextMenuAnon extends ContextMenu<ContextMenuAnon>{
		public ContextMenuAnon(ResizablePanel hrpParent) {
			super(hrpParent);
		}}
	/**
	 * use {@link ContextMenuAnon} for anonymous classes
	 */
	public static class ContextMenu<SELF extends ContextMenu<SELF>>{
		private DialogVisuals	vs;
		private LinkedHashMap<String,ContextButton> hmContextOptions = new LinkedHashMap<String,ContextButton>();
		private Panel pnlSourceLink;
		private ResizablePanel	rzpDialogHierarchyParent;
		private ContextButton	btnChoice;
		private boolean	bSingleChoiceMode;
		private String	strStyle;
		private Container	cntrContextOptions;
		private Button	btnTitle;
		
		private Command<Button>	cmdCloseOnClick = new Command<Button>() {
			@Override
			public void execute(Button source) {
				ContextButton cb = (ContextButton)source;
				if(!cb.isSubContextMenu()){
	//				hideContextMenu();
					vs.getDialog().close();
				}
			}
		};
		
		public String getReport(){
			return toString()+","+DialogHierarchyStateI.i().getHierarchyComp(vs.getDialog()).toString();
		}
		
		/**
		 * override for dynamic conxtext menu entries
		 */
		public void recreateEntries() {}
		
		public ContextMenu(ResizablePanel	rzpDialogHierarchyParent){
			this.rzpDialogHierarchyParent=rzpDialogHierarchyParent;
			
			strStyle = GuiGlobals.getInstance().getStyles().getDefaultStyle();
			
			vs = DialogHierarchyStateI.i().prepareDialogParts(ContextMenuI.class.getSimpleName(), strStyle);;
//			entid = DialogHierarchyStateI.i().getEntityId(rzpContextMenu); //DialogHierarchySystemI.i().createEntity(ContextMenuI.class.getSimpleName());
			
			vs.getDialog().addResizableListener(ContextMenuI.i());
			
			vs.ignorePositionRelativeToParent();
			
			MiscJmeI.i().addToName(vs.getDialog(), ContextMenuI.class.getSimpleName(), true);
			
			DialogHierarchySystemI.i().setHierarchyComp(vs.getEntityId(), new DiagCompBean()
				.setHierarchyType(EHierarchyType.Top)
				.setVolatileModal(true)
			);
			
			vs.getDialog().setAllEdgesEnabled(false); //it is here for the hierarchy (not the resizing)
			
			cntrContextOptions = new Container(strStyle);
			vs.getDialog().setContents(cntrContextOptions);
			vs.getDialog().setBackground(new QuadBackgroundComponent(
				ColorI.i().colorChangeCopy(ColorRGBA.Cyan, -0.5f, 0.75f)));
			
			btnTitle = new Button("");
			((TbtQuadBackgroundComponent)btnTitle.getBackground()).setColor(ColorRGBA.Blue);
			
//			CursorEventControl.addListenersToSpatial(rzpContextMenu, new ContextMenuListenerI());
			
			DialogHierarchyStateI.i().addRequestAutoFocus(vs.getDialog());
			
			if(ContextMenuI.i().bShowDbgInfo)QueueI.i().enqueue(cxDbgInfo);
		}
		
		private CallableX	cxDbgInfo = new CallableX() {
			@Override
			public Boolean call() {
				DialogHierarchyComp hc = DialogHierarchyStateI.i().getHierarchyComp(vs.getDialog());
				
				if(!vs.getDialog().isOpened())return true;
				
				//after diag hierarchy is ready
				if(hc.getLastFocusTime()==-1)return true;
				
				String str = hc.toString().replace(",", "\n");
				PopupHintHelpListenerI.i().setPopupHintHelp(btnTitle, str);
				MessagesI.i().debugInfo(this, str);
				
				return true;
			}
		}.setDelaySeconds(1f).enableLoopMode();
		private Object	objCurrentContextSourceStoredValue;
		
		/**
		 * will be set only when clicking, from {@link ContextMenuSourceCursorListenerX}
		 * @param pnlSource
		 * @return 
		 */
		private SELF setContextSource(Panel pnlSource){
			this.pnlSourceLink=pnlSource;
			return getThis();
		}
		
		/**
		 * see {@link #setContextSource(Panel)}
		 * @return
		 */
		@SuppressWarnings("unchecked")
		public <T extends Panel> T getContextSource(){
			return (T)pnlSourceLink;
		}
		
		public ContextMenu createSubMenu(String strTextKey){
			ContextMenu cmSub = new ContextMenu(vs.getDialog());
			
			ContextButton cb = addNewEntry(
				"[+] "+strTextKey, 
				/** a dummy cmd is necessary, there is already an expand toggler elsewhere */
				new ApplyContextChoiceCmd(){@Override public void executeContextCommand(ContextButton source){}}
			);
			cb.setSubContextMenu(true);
			
			ContextMenuI.i().applyContextMenuAtSource(cb, cmSub);
			
//			if(true)throw new UnsupportedOperationException(
//				"TODO: this requires the context menu to not be limited to 1");
			
			return cmSub;
		}
		
		/**
		 * this is the only way I found to let {@link Button#addClickCommands(Command...)} to compile... no casting possible.
		 */
		public static abstract class ApplyContextChoiceCmd implements Command<Button>{
			@Deprecated
			@Override
			public void execute(Button source) {
				assert(ContextButton.class.isInstance(source));
				executeContextCommand((ContextButton)source);
			}
			public abstract void executeContextCommand(ContextButton cbSource);
		}
		
		public ContextButton addNewEntry(String strTextKey, ApplyContextChoiceCmd cmd){
			return addNewEntry(strTextKey, null, cmd, null);
		}
		public ContextButton addNewEntry(String strTextKey, ApplyContextChoiceCmd cmd, HintUpdaterPerCtxtBtn hu){
			return addNewEntry(strTextKey, null, cmd, hu);
		}
		/**
		 * @param strTextKey
		 * @param cmd necessary because that is the whole point of it
		 * @param hu Each choice can have an exclusive {@link HintUpdaterPerCtxtBtn} that may use {@link HintUpdaterPerCtxtBtn#setPopupHintHelp(String)} (for the single choice mode, there is an automatic popup hint for that)
		 * @return
		 */
		public ContextButton addNewEntry(String strTextKey, Object objStoreContextItemValue, ApplyContextChoiceCmd cmd, HintUpdaterPerCtxtBtn hu){
			assert(cmd!=null);
			ContextButton cb = new ContextButton(strTextKey);
			AbsorbClickCommandsI.i().absorbClickCommands(cb);
			cb.cmParent=this;
			ContextMenuI.i().applyContextButtonListener(cb,cmd); //cb.addClickCommands(cmd); //TODO use a context cursor listener...
//			if(cxHintUpdater!=null)UserDataI.i().setUserDataPSH(btn, EContext.HintUpdater, cxHintUpdater);
			if(hu!=null){
				cb.setHintUpdater(hu);
				hu.setContextButtonParent(cb);
			}
			hmContextOptions.put(strTextKey, cb);
			cb.setStoredValue(objStoreContextItemValue);
			return cb;
		}
		
		public ResizablePanel getDialogHierarchyParent() {
			return rzpDialogHierarchyParent;
		}

		private SELF setSingleChoice(ContextButton btnChoice) {
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

		public SELF setCurrentContextSourceStoredValue(Object obj) {
			this.objCurrentContextSourceStoredValue=obj;
			return getThis();
		}

		@SuppressWarnings("unchecked")
		public <T> T getCurrentContextSourceStoredValue() {
			return (T)objCurrentContextSourceStoredValue;
		}

		public void close() {
			vs.getDialog().close();
		}
		
	}
	
	/**
	 * {@link ContextButton} 1 to 1 {@link HintUpdaterPerCtxtBtn}<br>
	 * <br>
	 * see {@link ContextMenuI#showContextMenu(Vector2f, String, ContextMenu)}<br>
	 * <br>
	 * use {@link #setPopupHintHelp(String)} to apply a custom value, otherwise default will be used<br>
	 * use {@link #getStoredValueFromContextButton()} to compare the context source value with linked contextmenu value<br>
	 */
	public static abstract class HintUpdaterPerCtxtBtn extends CallableX<HintUpdaterPerCtxtBtn>{
		private ContextButton	cbParent;
		private String strPopupHintHelp;

		public void setPopupHintHelp(String str){
			this.strPopupHintHelp=str;
		}

		public String getPopupHintHelp() {
			return strPopupHintHelp;
		}
		
		private void setContextButtonParent(ContextButton cb){
			DetailedException.assertNotAlreadySet(this.cbParent, cb, 
				"the "+this.getClass().getSimpleName()+" is exclusive to a single "+cb.getClass().getSimpleName(),
				this);
			this.cbParent=cb;
		}
		
		@SuppressWarnings("unchecked")
		public <T> T getStoredValueFromContextButton(){
			return (T)cbParent.getStoredValue();
		}
		
		/**
		 * 
		 * @return the context button for this {@link HintUpdaterPerCtxtBtn}
		 */
		public ContextButton getContextButtonParent(){
			return this.cbParent;
		}
		
		@Override
		protected HintUpdaterPerCtxtBtn getThis() {
			return this;
		}
	}
	
	public static class ContextButton<SELF extends ContextButton<SELF>> extends Button{
		private HintUpdaterPerCtxtBtn	cxHintUpdater;
		private boolean	bSubContextMenu;
		private Object val;
//		private Command<ContextButton>	cmd;
		private ApplyContextChoiceCmd	cmd;
		private ContextMenu	cmParent;

		private ContextButton(String s) {
			super(s);
		}
		
//		@Deprecated
//		@Override
//		public void addClickCommands(Command<? super Button>... commands) {
//			throw new DeprecationException("method not implemented yet");
//		}
		
		private SELF setSubContextMenu(boolean bSubContextMenu){
			this.bSubContextMenu=bSubContextMenu;
			return getThis();
		}
		
		public boolean isSubContextMenu() {
			return bSubContextMenu;
		}
		
		private SELF setHintUpdater(HintUpdaterPerCtxtBtn cxHintUpdater) {
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

		private HintUpdaterPerCtxtBtn getHintUpdater() {
			return cxHintUpdater;
		}
		
		@SuppressWarnings("unchecked")
		public <T> T getStoredValue() {
			return (T)val;
		}

		public SELF setStoredValue(Object val) {
			this.val=val;
			return getThis();
		}

		public void setContextCommand(ApplyContextChoiceCmd cmd) {
			this.cmd=cmd;
		}

		public void executeContextCommand() {
			cmd.executeContextCommand(this);
		}

		public ContextMenu getContextMenuParent() {
			return cmParent;
		}
	}
	
//	private class ContextMenuListenerI extends DefaultCursorListener{
//		@Override
//		public void cursorExited(CursorMotionEvent event, Spatial target,				Spatial capture) {
//			super.cursorExited(event, target, capture);
//			//TODO not working well, see hideContextMenu(); 
//		}
//	}
	
	public static class ContextChoiceMadeCursorListenerX extends CursorListenerX{
//		public static ContextMenuOwnerListenerI i(){return GlobalManagerI.i().get(ContextMenuOwnerListenerI.class);}
		
		@Override
		protected boolean click(CursorButtonEvent event, Spatial target,				Spatial capture) {
//			super.click(event, target, capture);
			
			if(event.getButtonIndex()!=0)return false; //left mouse button
			
			ContextButton btn = (ContextButton)capture;
			btn.executeContextCommand(); //shall set the context value at the context source 
			btn.getContextMenuParent().setCurrentContextSourceStoredValue(btn.getStoredValue());
			
			return true;
		}

	}
	ContextChoiceMadeCursorListenerX contextChoiceMadeListener = new ContextChoiceMadeCursorListenerX();
	
	public static class ContextMenuSourceCursorListenerX extends CursorListenerX{
		@Override
		protected boolean click(CursorButtonEvent event, Spatial target,				Spatial capture) {
			if(event.getButtonIndex()!=1)return false; //only right mouse button
			
			Button btn = (Button)capture;
			
			ContextMenu cm = UserDataI.i().getMustExistOrNull(btn, ContextMenu.class);
			if(cm!=null){
				ContextMenuI.i().showContextMenu(event.getLocation(), btn.getText(), cm.setContextSource(btn));
				return true;
			}
			
			return false;
		}
		
		@Override
		public void cursorEntered(CursorMotionEvent event, Spatial target, Spatial capture) {
			super.cursorEntered(event, target, capture);
			ContextMenuI.i().giContextMenuAvailableIndicator.setEnabled(true).setTargetAndEnable((Node)target);
		}
		
		@Override
		public void cursorExited(CursorMotionEvent event, Spatial target,Spatial capture) {
			super.cursorExited(event, target, capture);
			ContextMenuI.i().giContextMenuAvailableIndicator.setEnabled(false);
		}
	}
	ContextMenuSourceCursorListenerX contextMenuSourceCursorListenerX = new ContextMenuSourceCursorListenerX();
	
	public void configure(){//Node nodeParent) {
//		strStyle = GuiGlobals.getInstance().getStyles().getDefaultStyle();
//		
//		rzpContextMenu = DialogHierarchyStateI.i().createDialog(ContextMenuI.class.getSimpleName(), strStyle);
//		entid = DialogHierarchyStateI.i().getEntityId(rzpContextMenu); //DialogHierarchySystemI.i().createEntity(ContextMenuI.class.getSimpleName());
//		
//		rzpContextMenu.addResizableListener(this);
//		
//		DialogHierarchyStateI.i().getVisuals(rzpContextMenu).ignorePositionRelativeToParent();
//		
//		MiscJmeI.i().addToName(rzpContextMenu, ContextMenuI.class.getSimpleName(), true);
//		
//		DialogHierarchySystemI.i().setHierarchyComp(entid, 
//			EField.eHierarchyType, EHierarchy.Top,
//			EField.bVolatileModal, true
//		);
//		
//		rzpContextMenu.setAllEdgesEnabled(false); //it is here for the hierarchy (not the resizing)
//		
//		cntrContextOptions = new Container(strStyle);
//		rzpContextMenu.setContents(cntrContextOptions);
//		rzpContextMenu.setBackground(new QuadBackgroundComponent(ColorRGBA.Cyan));
//		
//		lbl = new Label("");
//		
////		CursorEventControl.addListenersToSpatial(rzpContextMenu, new ContextMenuListenerI());
//		
//		DialogHierarchyStateI.i().addRequestAutoFocus(rzpContextMenu);
		
		giContextMenuAvailableIndicator = IndicatorI.i().createIndicator(ColorI.i().colorChangeCopy(ColorRGBA.Cyan,0f,0.75f));
		giContextMenuAvailableIndicator.setDenyDestruction();
	}
	
	public int applyContextMenuAtListBoxItems(ListBox lstbx, ContextMenu cm){
//		if(!applyContextMenuAtSource(lstbx, cm))return 0; //TODO works?
		
//		ArrayList<Panel> apnl = MiscJmeI.i().getAllChildrenRecursiveFrom(lstbx.getGridPanel(), Panel.class, null);
		ArrayList<Panel> apnl = MiscLemurI.i().getAllListBoxItems(lstbx,false);
		int i=0;
		for(Panel pnl:apnl){
			if(applyContextMenuAtSource(pnl, cm))i++;
		}
		return i;
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
	 * @param sptContextClick
	 * @param cm
	 * @return true if was just applied, false if was already applied before and did nothing now
	 */
	public boolean applyContextMenuAtSource(Spatial sptContextClick, ContextMenu cm){
		if(UserDataI.i().contains(sptContextClick, ContextMenu.class)){
			return false; //already set
		}
		
		UserDataI.i().putSafelyMustNotExist(sptContextClick, cm);
		AbsorbClickCommandsI.i().absorbClickCommands(sptContextClick);
		CursorEventControl.addListenersToSpatial(sptContextClick, contextMenuSourceCursorListenerX);
		
		if(bUseContextMenuAvailablePermanentIndicators){
			permanentIndicator(sptContextClick);
		}
		
		return true;
	}
	
	private void applyContextButtonListener(ContextButton btn, ApplyContextChoiceCmd cmd){
		btn.setContextCommand(cmd);
		CursorEventControl.addListenersToSpatial(btn, contextChoiceMadeListener);
	}
	
	/**
	 * the popup hint help will only be set if {@link HintUpdaterPerCtxtBtn#call()} succeeds 
	 * 
	 * @param v2fMouseCursorPos
	 * @param strContextMenuTitle
	 * @param cm
	 */
	@SuppressWarnings("unchecked")
	private void showContextMenu(Vector2f v2fMouseCursorPos, String strContextMenuTitle, ContextMenu<?> cm) {
		cm.cntrContextOptions.clearChildren();
		
		int i=0;
		cm.btnTitle.setText("Context:"+strContextMenuTitle);
		DragParentestPanelListenerI.i().applyAt(cm.btnTitle);
		cm.cntrContextOptions.addChild(cm.btnTitle, i++, 0);
		
		cm.recreateEntries();
		assert(cm.hmContextOptions.size()>0);
		
		for(Entry<String, ContextButton> entry : (cm.hmContextOptions).entrySet()){
			ContextButton cbChoice = entry.getValue();
			
			AbsorbClickCommandsI.i().addClickCommands(cbChoice, cm.cmdCloseOnClick);
//			cbChoice.addClickCommands(cm.cmdCloseOnClick);
			
			// popup hint help
			PopupHintHelpListenerI.i().resetPopupHelp(cbChoice); //clear
			HintUpdaterPerCtxtBtn cxHU = cbChoice.getHintUpdater();
			if(cxHU!=null){
				cxHU.setPopupHintHelp(null);
				if(cxHU.call()){
					if(cm.isSingleChoiceMode()){
						if(cxHU.getPopupHintHelp()==null)cxHU.setPopupHintHelp("currently chosen");
						cm.setSingleChoice(cbChoice);
						GeomIndicator gi = IndicatorI.i().createIndicator(ColorRGBA.Yellow)
							.setTargetAndEnable(cbChoice);
					}
					
					PopupHintHelpListenerI.i().setPopupHintHelp(cbChoice, cxHU.getPopupHintHelp());
				}
			}
			
			cm.cntrContextOptions.addChild(cbChoice, i++, 0);
		}
		
		DialogHierarchyStateI.i().showDialogAsModal(cm.getDialogHierarchyParent(), cm.vs.getDialog());
		
//		cm.hmContextOptions.get(0).getsi
		cm.vs.getDialog().setPreferredSizeWH(
			new Vector3f(
				200, //TODO find a better width
				30*cm.hmContextOptions.size(), //TODO find a better height, based on bitmap font height? or button height? or adjust the size later after cm is ready? 
				0)//cm.rzpContextMenu.getPreferredSize().z)
		);
		
		int iDisplacement=20;
		Vector3f v3f = 
	//		cm.vs.getDialog().setLocalTranslationXY(
			new Vector3f(
				v2fMouseCursorPos.getX()-iDisplacement, 
				v2fMouseCursorPos.getY()+iDisplacement, 
				0); // z will be fixed by diag hierarchy
		QueueI.i().enqueue(new CallableXAnon() { //later to let the diag settle down in size
			@Override
			public Boolean call() {
				if(!cm.vs.getDialog().isOpened())return false;
				if(cm.vs.getDialog().getSize().length()==0)return false; //wait it be ready
				SizeAndLocationI.i().positionFullyInsideScreenLimits(cm.vs.getDialog(),v3f);
				return true;
			}
		});
//		SizeAndLocationI.i().setLocalTranslationZ(cm.vs.getDialog(), MiscJmeI.i().getZAboveAllAtGuiNode());
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ContextMenuI [bUseContextMenuAvailablePermanentIndicators=");
		builder.append(bUseContextMenuAvailablePermanentIndicators);
		builder.append(", giContextMenuAvailableIndicator=");
		builder.append(giContextMenuAvailableIndicator);
		builder.append(", bShowDbgInfo=");
		builder.append(bShowDbgInfo);
		builder.append("]");
		return builder.toString();
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
	
	@Override	public void resizableUpdatedLogicalStateEvent(float tpf,			ResizablePanel rzpSource) {	}
	@Override
	public void resizableRemovedFromParentEvent(ResizablePanel rzpSource) {
		IndicatorI.i().destroyAllIndicatorsRecursively(rzpSource);
//		hideContextMenu();
	}
	@Override	public void resizableStillResizingEvent(ResizablePanel rzpSource, Vector3f v3fNewSize) {	}
	@Override	public void resizableEndedResizingEvent(ResizablePanel rzpSource) {	}

	public ContextMenu createStringRegexOptContextMenu(
		ResizablePanel rzpDialogHierarchyParent, 
		EStringMatchMode eCurrentValue, 
		ApplyContextChoiceCmd cmd
	) {
		ContextMenu cm=new ContextMenu(rzpDialogHierarchyParent);
		
		cm.setCurrentContextSourceStoredValue(eCurrentValue);
		
		cm.setSingleChoiceMode(true);
		for(EStringMatchMode eToBeStored:EStringMatchMode.values()){
			HintUpdaterPerCtxtBtn hu = new HintUpdaterPerCtxtBtn() {
				@Override
				public Boolean call() {
//					throw new UnsupportedOperationException("method not implemented yet");
//					cm.getContextSource();
					EStringMatchMode eStored = getStoredValueFromContextButton();
//					return getContextButtonLinked().getText().equals(e.s());
					return cm.getCurrentContextSourceStoredValue().equals(eStored);
				}
			};
			
			cm.addNewEntry(eToBeStored.s(), eToBeStored, cmd, hu);
		}
		
		return cm;
	}

	public void removeContextMenuOf(Button btn) {
		CursorEventControl.removeListenersFromSpatial(btn,contextMenuSourceCursorListenerX);
	}
	
//  private void tstTmp( Command<? super Button>... commands ) {}
//  private void tstTmp2( Command<? super Button> command ) {}
//	private static class Tst extends Button{		public Tst(String s) {			super(s);		}	}
//	public static abstract class CmdTst implements Command<Button>{
//		@Deprecated
//		@Override
//		public void execute(Button source) {
//			executeContextCommand((ContextButton)source);
//		}
//		public abstract void executeContextCommand(ContextButton source);
//	}
//	private void test(){
//		tstTmp2(new CmdTst());
//		tstTmp2(new Command<Tst>(){			@Override			public void execute(Tst source) {	throw new UnsupportedOperationException("method not implemented yet");			}});
//		Button btn;btn.addClickCommands(new Command<Tst>(){			@Override			public void execute(Tst source) {				throw new UnsupportedOperationException("method not implemented yet");			}});
//	}
}
