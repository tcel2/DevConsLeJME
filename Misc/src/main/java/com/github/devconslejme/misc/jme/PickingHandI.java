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

package com.github.devconslejme.misc.jme;

import java.util.ArrayList;

import com.github.devconslejme.devcons.LoggingI;
import com.github.devconslejme.misc.GlobalManagerI;
import com.github.devconslejme.misc.GlobalManagerI.G;
import com.jme3.app.Application;
import com.jme3.collision.CollisionResults;
import com.jme3.input.FlyByCamera;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

/**
 * @author Henrique Abdalla <https://github.com/AquariusPower><https://sourceforge.net/u/teike/profile/>
 */
public class PickingHandI {
	public static PickingHandI i(){return GlobalManagerI.i().get(PickingHandI.class);}
	
	private CollisionResults	crLastPick;
	private Ray	rayLastCast;
	ArrayList<IPickListener> aplList = new ArrayList<IPickListener>();
	private boolean	bAllowConsume=true;
	
	public static interface IPickListener{
		/**
		 * 
		 * @param cr
		 * @param geom
		 * @param sptParentest
		 * @return true if consumed TODO prioritize listeners?
		 */
		boolean updatePickingEvent(CollisionResults cr, Geometry geom, Spatial sptParentest);
	}
	
	public  void addListener(IPickListener l){
		if(!aplList.contains(l))aplList.add(l);
	}
	
	public void configure(FlyByCamera flycam){
		String strPck="PickVirtualWorldThing";
		G.i(Application.class).getInputManager().addMapping(strPck, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
		G.i(Application.class).getInputManager().addListener(new ActionListener() {
				@Override
				public void onAction(String name, boolean isPressed, float tpf) {
					if(flycam!=null && flycam.isEnabled())return;
					
					if(!isPressed && name.equals(strPck)){ //on release
						PickingHandI.i().pickWorldSpatialAtCursor();
//						Spatial spt = PickingHandI.i().pickWorldSpatialAtCursor();
//						if(spt!=null)LoggingI.i().logMarker(spt.toString());
					}
				}
			},
			strPck
		);
	}
	
	public Spatial pickWorldSpatialAtCursor(){
		CollisionResults cr = pickWorldPiercingAtCursor();
		if(cr==null)return null;
		return cr.getClosestCollision().getGeometry();
	}
	public CollisionResults pickWorldPiercingAtCursor(){
		return pickWorldPiercingAtCursor(MiscJmeI.i().getNodeVirtualWorld());
	}
	public CollisionResults pickWorldPiercingAtCursor(Node nodeVirtualWorld){
		crLastPick = new CollisionResults();
		
		Vector3f v3fCursorAtVirtualWorld3D = MiscJmeI.i().getApp().getCamera().getWorldCoordinates(
			EnvironmentJmeI.i().getMouse().getPos2D(), 0f);
		
		Vector3f v3fDirection = MiscJmeI.i().getApp().getCamera().getWorldCoordinates(
			EnvironmentJmeI.i().getMouse().getPos2D(), 1f);
		v3fDirection.subtractLocal(v3fCursorAtVirtualWorld3D).normalizeLocal();
		
		rayLastCast = new Ray(v3fCursorAtVirtualWorld3D, v3fDirection);
		nodeVirtualWorld.collideWith(rayLastCast, crLastPick);

		if(crLastPick.size()==0)crLastPick=null;
		
		for(IPickListener l:aplList){
			if(crLastPick!=null){
				if(l.updatePickingEvent(crLastPick, getLastWorldPick(), getLastWorldPickParentest())){
					if(bAllowConsume)break;
				}
			}else{
				l.updatePickingEvent(null,null,null);
			}
		}
		
		return crLastPick;
	}
	
	/**
	 * 
	 * @return can be a Geometry or a Node
	 */
	public Spatial getLastWorldPickParentest(){
		Geometry geom = getLastWorldPick();
		if(geom==null)return null;
		
		if(getLastWorldPick().getParent()==MiscJmeI.i().getNodeVirtualWorld()){
			return getLastWorldPick();
		}
		
		return SpatialHierarchyI.i().getParentest(getLastWorldPick(), Node.class, true, false);
//		ArrayList<Node> anode = SpatialHierarchyI.i().getAllParents(getLastWorldPick(),false);
//		return anode.get(anode.size()-1);
	}
	public Geometry getLastWorldPick(){
		if(crLastPick==null)return null;
		if(crLastPick.getClosestCollision()==null)return null;
		return crLastPick.getClosestCollision().getGeometry();
	}
	public CollisionResults getLastWorldPiercingPick(){
		return crLastPick;
	}
	public Ray getRayLastCast() {
		return rayLastCast;
	}

	public boolean isAllowConsume() {
		return bAllowConsume;
	}

	public PickingHandI setAllowConsume(boolean bAllowConsume) {
		this.bAllowConsume = bAllowConsume;
		return this; //for beans setter
	}
}
