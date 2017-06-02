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
package com.github.devconslejme.game;

import java.util.ArrayList;
import java.util.HashMap;

import com.github.devconslejme.game.TargetI.TargetGeom;
import com.github.devconslejme.misc.DetailedException;
import com.github.devconslejme.misc.GlobalManagerI;
import com.github.devconslejme.misc.InfoI;
import com.github.devconslejme.misc.InfoI.Info;
import com.github.devconslejme.misc.KeyBindCommandManagerI;
import com.github.devconslejme.misc.KeyBindCommandManagerI.CallBoundKeyCmd;
import com.github.devconslejme.misc.MessagesI;
import com.github.devconslejme.misc.QueueI;
import com.github.devconslejme.misc.QueueI.CallableXAnon;
import com.github.devconslejme.misc.jme.AppI;
import com.github.devconslejme.misc.jme.ColorI;
import com.github.devconslejme.misc.jme.GeometryI;
import com.github.devconslejme.misc.jme.InfoJmeI;
import com.github.devconslejme.misc.jme.MeshI;
import com.github.devconslejme.misc.jme.UserDataI;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.BulletAppState.ThreadingType;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;

/**
 * @author Henrique Abdalla <https://github.com/AquariusPower><https://sourceforge.net/u/teike/profile/>
 */
public class PhysicsI implements PhysicsTickListener{
	public static PhysicsI i(){return GlobalManagerI.i().get(PhysicsI.class);}
	
	public static class Impulse{
		private Spatial	spt;
		private RigidBodyControl	rbc;
		/** @DefSelfNote dont expose it, this class is a simplifier */
		private PhysicsSpace	ps;
		
		private Vector3f	v3fForce;
		private Vector3f	v3fForceLocation;
		private Vector3f	v3fRelPos;
		private Vector3f	v3fImpulse;
		private Vector3f	v3fTorque;
		private Vector3f	v3fTorqueImpulse;
		private Float	fImpulse;
		
		public Spatial getSpt() {
			return spt;
		}
		public Impulse setSpt(Spatial spt) {
			this.spt = spt;
			return this;
		}
		public RigidBodyControl getRBC() {
			return rbc;
		}
		public Impulse setRBC(RigidBodyControl rbc) {
			this.rbc = rbc;
			return this;
		}
		
		public PhysicsSpace getPs() {
			return ps;
		}
		
		public Vector3f getForce() {
			return v3fForce;
		}
		public Impulse setForce(Vector3f v3fForce) {
			this.v3fForce = v3fForce;
			return this;
		}
		public Vector3f getForceLocation() {
			return v3fForceLocation;
		}
		public Impulse setForceLocation(Vector3f v3fForceLocation) {
			this.v3fForceLocation = v3fForceLocation;
			return this;
		}
		public Vector3f getRelPos() {
			return v3fRelPos;
		}
		public Impulse setRelPos(Vector3f v3fRelPos) {
			this.v3fRelPos = v3fRelPos;
			return this;
		}
		public Vector3f getImpulse() {
			return v3fImpulse;
		}
		public Impulse setImpulseAtDir(float fImpulse) {
			this.fImpulse=fImpulse;
			return this;
		}
		public Impulse setImpulse(Vector3f v3fImpulse) {
			this.v3fImpulse = v3fImpulse;
			return this;
		}
		public Vector3f getV3fTorque() {
			return v3fTorque;
		}
		public Impulse setTorque(Vector3f v3fTorque) {
			this.v3fTorque = v3fTorque;
			return this;
		}
		public Vector3f getTorqueImpulse() {
			return v3fTorqueImpulse;
		}
		public Impulse setTorqueImpulse(Vector3f v3fTorqueImpulse) {
			this.v3fTorqueImpulse = v3fTorqueImpulse;
			return this;
		}
		
	}

	private BulletAppState	bullet;
	private PhysicsSpace	ps;
	
	public void configure(){
		bullet = new BulletAppState();
		bullet.setThreadingType(ThreadingType.PARALLEL);
		AppI.i().attatchAppState(bullet);
		
//		QueueI.i().enqueue(new CallableXAnon() {
//			@Override
//			public Boolean call() {
				ps = bullet.getPhysicsSpace();
				ps.addTickListener(PhysicsI.this);
//				return true;
//			}
//		});
				
		initUpdateLastTargetInfo();
	}
	
	private ArrayList<Impulse> arbcQueue = new ArrayList();
//	private boolean	bTestBuggyFloor;
	
	/**
	 * 
	 * @param spt
	 * @return dynamic: mass 1f
	 */
	public PhysicsData imbueFromWBounds(Spatial spt){
		return imbueFromWBounds(spt,null);
	}
	public static class PhysicsData{
		float fMass;
		Quaternion	quaBkp;
		BoundingVolume	bv;
		BoundingBox	bb;
		CollisionShape	cs;
		BoundingSphere	bs;
		RigidBodyControl	rbc;
		Spatial	sptLink;
		Float	fDensity;
		public float	fVolume;
		
		public PhysicsData(Spatial spt) {
			this.sptLink = spt;
		}

		public PhysicsRigidBody getRBC() {
			return rbc;
		}
	}
	public PhysicsData imbueFromWBounds(Spatial spt, Float fDensityAutoMassFromVolume){
		assert !UserDataI.i().contains(spt, PhysicsData.class);
		
		PhysicsData pd = new PhysicsData(spt);
		pd.fDensity=fDensityAutoMassFromVolume;
		
		//bkp rot
		pd.quaBkp = spt.getWorldRotation().clone();
		spt.lookAt(spt.getLocalTranslation().add(0,0,1), Vector3f.UNIT_Y);
		
		pd.bv = spt.getWorldBound().clone(); //the world bound will already be a scaled result...
		
		//restore rot
		spt.lookAt(spt.getLocalTranslation().add(pd.quaBkp.getRotationColumn(2)), pd.quaBkp.getRotationColumn(1));
		
		// create collision shape from bounds
		float fPseudoDiameter = 0f;
		if (pd.bv instanceof BoundingBox) {
			pd.bb = (BoundingBox) pd.bv.clone();
			pd.cs = new BoxCollisionShape(pd.bb.getExtent(null));
			fPseudoDiameter=2f*pd.bb.getExtent(null).length();
		}else
		if (pd.bv instanceof BoundingSphere) {
			pd.bs = (BoundingSphere) pd.bv.clone();
			pd.cs = new SphereCollisionShape(pd.bs.getRadius());
			fPseudoDiameter=2f*pd.bs.getRadius();
		}else{
			throw new DetailedException("unsupported "+pd.bv.getClass(),spt);
		}
		
//		if(false)assert spt.getWorldScale().lengthSquared()==3f : "scaled collision shape may cause imprecision and even ccd will fail";
//		if(spt.getWorldScale().lengthSquared()!=3f){ //TODO enable this one day?
//			cs.setScale(spt.getWorldScale());
//		}
		
		pd.fVolume = pd.bv.getVolume();
		
		pd.rbc=new RigidBodyControl(pd.cs);
		pd.fMass=1f;
		if(pd.fDensity!=null){
			pd.fMass=pd.fVolume*pd.fDensity;
		}
		pd.rbc.setMass(pd.fMass);
		
//		float fCCdMotionThreshold = fPseudoDiameter;
//		float fCCdMotionThreshold = fPseudoDiameter*0.75f;
		float fCCdMotionThreshold = fPseudoDiameter/2f;
		pd.rbc.setCcdMotionThreshold(fCCdMotionThreshold);
		
		/**
		 * "Each time the object moves more than (motionThreshold) within one frame a sphere of radius 
		 * (sweptSphereRadius) is swept from the first position of the object to the position in the 
		 * next frame to check if there was any objects in between that were missed because the object 
		 * moved too fast." - https://hub.jmonkeyengine.org/t/ccd-usage/24655/13
		 * 
		 * "The radius is just the radius of the sphere that is swept to do this check, so make it so 
		 * large that it resembles your object." - https://hub.jmonkeyengine.org/t/ccd-usage/24655/2
		 */
		pd.rbc.setCcdSweptSphereRadius(fPseudoDiameter/2f);
		
		spt.addControl(pd.rbc);
		
		ps.add(spt);
		
		UserDataI.i().putSafelyMustNotExist(spt, pd);
		
		return pd;
	}
	
	public PhysicsData getPhysicsDataFrom(Spatial spt){
		return UserDataI.i().getMustExistOrNull(spt, PhysicsData.class);
	}
	
	private void initUpdateLastTargetInfo() {
		QueueI.i().enqueue(new CallableXAnon() {
			@Override
			public Boolean call() {
				TargetGeom tgt = TargetI.i().getLastSingleTarget();
				if(tgt!=null){
					PhysicsData pd = getPhysicsDataFrom(tgt.getGeometryHit());
					if(pd!=null){
						HashMap<String, Info> hm = tgt.getOrCreateSubInfo("Phys");
						InfoJmeI.i().putAt(hm,"mass",pd.fMass,3);
						InfoJmeI.i().putAt(hm,"vol",pd.fVolume,3);
						InfoJmeI.i().putAt(hm,"spd",pd.rbc.getLinearVelocity(),2);
						InfoJmeI.i().putAt(hm,"angv",pd.rbc.getAngularVelocity(),1);
						InfoJmeI.i().putAt(hm,"grav",pd.rbc.getGravity(),1);
					}
				}
				return true;
			}
		}).enableLoopMode().setDelaySeconds(0.5f);
	}
	
	public PhysicsData erasePhysicsFrom(Spatial spt){
		RigidBodyControl rbc = spt.getControl(RigidBodyControl.class);
		spt.removeControl(rbc);
		remove(spt);
		PhysicsData pd = getPhysicsDataFrom(spt);
		UserDataI.i().eraseAllOf(spt,pd);
		return pd;
	}
	
	public void remove(Spatial spt){
		ps.remove(spt);
	}
	
	public void add(Spatial spt){
		ps.add(spt);
	}
	
	public void enqueue(Spatial spt, Impulse imp){
//		if(obj instanceof Spatial){
			imp.spt = spt;//(Spatial)obj;
			imp.rbc = imp.spt.getControl(RigidBodyControl.class);
//		}else
//		if(obj instanceof RigidBodyControl){
//			imp.rbc=(RigidBodyControl)obj;
//		}else{
//			throw new DetailedException("unsupported type "+obj.getClass().getName(),obj,imp);
//		}
		imp.ps=ps;
		
		synchronized (arbcQueue) {
			arbcQueue.add(imp);
		}
	}

	@Override
	public void prePhysicsTick(PhysicsSpace ps, float tpf) {
		synchronized(arbcQueue){
			for(Impulse imp:arbcQueue){
				assert imp.ps==ps;
				
				if(imp.v3fForce!=null){
					if(imp.v3fForceLocation==null){
						imp.rbc.applyCentralForce(imp.v3fForce);
					}else{
						imp.rbc.applyForce(imp.v3fForce, imp.v3fForceLocation);
					}
				}
				
				if(imp.fImpulse!=null){
					imp.rbc.applyImpulse(
						imp.rbc.getPhysicsRotation().getRotationColumn(2).mult(imp.fImpulse), 
						Vector3f.ZERO
					);
				}
				
				if(imp.v3fImpulse!=null && imp.v3fRelPos!=null)
					imp.rbc.applyImpulse(imp.v3fImpulse, imp.v3fRelPos);
				
				if(imp.v3fTorque!=null)
					imp.rbc.applyTorque(imp.v3fTorque);
				
				if(imp.v3fTorqueImpulse!=null)
					imp.rbc.applyTorque(imp.v3fTorqueImpulse);
				
			}
			
			arbcQueue.clear();
		}
	}

	/**
	 * TODO write current forces to spatials for easy access?
	 */
	@Override
	public void physicsTick(PhysicsSpace ps, float tpf) {
//		ps.getWorldMax()
	}
	
//	public void initTest(boolean bTestBuggyFloor){
	public void initTest(){
		setDebugEnabled(true);
//		SubdivisionSurfaceModifier s = new SubdivisionSurfaceModifier(modifierStructure, blenderContext);
		
		int iSize=50;
		Geometry geomFloor=null;
//		if(false){
//		MBox mb = new MBox(iSize, 0.1f, iSize, iSize, 1, iSize);
//		geomFloor = GeometryI.i().create(mb, ColorI.i().colorChangeCopy(ColorRGBA.Green,-0.5f,1f));
//		}else{
//		if(bTestBuggyFloor){
//			geomFloor = GeometryI.i().create(MeshI.i().box(0.5f), ColorI.i().colorChangeCopy(ColorRGBA.Brown,0.25f,1f));
//			geomFloor.scale(iSize, 0.1f, iSize); //b4 imbue
//		}else{
//		geomFloor = GeometryI.i().create(new Box(iSize,0.1f,iSize), ColorI.i().colorChangeCopy(ColorRGBA.Green,-0.5f,1f));
			geomFloor = GeometryI.i().create(new Box(iSize,0.1f,iSize), ColorI.i().colorChangeCopy(ColorRGBA.Brown,0.20f,1f));
//		}
		geomFloor.move(0,-7f,0);
		PhysicsI.i().imbueFromWBounds(geomFloor).getRBC().setMass(0f);
		AppI.i().getRootNode().attachChild(geomFloor);
		
		{
		Geometry geom = GeometryI.i().create(MeshI.i().box(0.5f), ColorRGBA.Red);
		imbueFromWBounds(geom,3f);
		AppI.i().getRootNode().attachChild(geom);
		}{
		Geometry geom = GeometryI.i().create(MeshI.i().box(0.5f), ColorRGBA.Green);
		imbueFromWBounds(geom,2f);
		AppI.i().getRootNode().attachChild(geom);
		}{
		Geometry geom = GeometryI.i().create(MeshI.i().box(0.5f), ColorRGBA.Blue);
		imbueFromWBounds(geom,1f);
		AppI.i().getRootNode().attachChild(geom);
		}
		
    KeyBindCommandManagerI.i().putBindCommandsLater("Space",new CallBoundKeyCmd(){
    		@Override	public Boolean callOnKeyPressed(int iClickCountIndex){
    			testCamProjectile(100,0.1f,6f);			return true;}
			}.setName("TestShootProjectile").holdKeyPressedForContinuousCmd().setDelaySeconds(0.33f)
		);
	}
	
	public void setEnabled(boolean enabled) {
		bullet.setEnabled(enabled);
	}

	public boolean isEnabled() {
		return bullet.isEnabled();
	}

	public void setDebugEnabled(boolean debugEnabled) {
		bullet.setDebugEnabled(debugEnabled);
	}

	public boolean isDebugEnabled() {
		return bullet.isDebugEnabled();
	}

	public float getSpeed() {
		return bullet.getSpeed();
	}

	public void setSpeed(float speed) {
		bullet.setSpeed(speed);
	}
	
	/**
	 * Do not use with bullets, they are too tiny, too little mass, too fast...
	 * For such bullets use raycast and apply forces on the hit target.
	 * @param spt
	 * @param fImpulseAtDirection
	 */
	public void throwAtSelfDirImpulse(Spatial spt, float fImpulseAtDirection){
		PhysicsData pd = getPhysicsDataFrom(spt);
		if(pd!=null && pd.fMass<0.01f && fImpulseAtDirection>300){
			MessagesI.i().warnMsg(this, "this looks like a bullet, avoid using this method!", spt, pd, fImpulseAtDirection);
		}
		PhysicsI.i().enqueue(spt, new Impulse().setImpulseAtDir(fImpulseAtDirection));
	}
	
	public Object debugTest(Object... aobj){
		return null; //keep even if emtpy
	}
	public void testCamProjectile(float fDesiredSpeed, float fRadius, float fDensity){
		Geometry geom = GeometryI.i().create(MeshI.i().sphere(fRadius), ColorRGBA.Yellow);
		AppI.i().placeAtCamWPos(geom, 1f, true);
		AppI.i().getRootNode().attachChild(geom);
		
		PhysicsData pd = PhysicsI.i().imbueFromWBounds(geom,fDensity);
		PhysicsI.i().throwAtSelfDirImpulse(geom, fDesiredSpeed*pd.fMass); //the final speed depends on the mass
	}

	public void syncPhysTransfFromSpt(Spatial spt) {
		RigidBodyControl rbc = spt.getControl(RigidBodyControl.class);
		rbc.setPhysicsLocation(spt.getWorldTranslation());
		rbc.setPhysicsRotation(spt.getWorldRotation());
	}
}
