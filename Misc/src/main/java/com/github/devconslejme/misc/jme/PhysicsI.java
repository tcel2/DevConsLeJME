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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import com.github.devconslejme.game.CharacterI.LeviCharacter;
import com.github.devconslejme.misc.Annotations.NotMainThread;
import com.github.devconslejme.misc.Annotations.Workaround;
import com.github.devconslejme.misc.DetailedException;
import com.github.devconslejme.misc.GlobalManagerI;
import com.github.devconslejme.misc.InfoI.Info;
import com.github.devconslejme.misc.MainThreadI;
import com.github.devconslejme.misc.MatterI.EMatter;
import com.github.devconslejme.misc.MatterI.Matter;
import com.github.devconslejme.misc.MatterI.MatterStatus;
import com.github.devconslejme.misc.MessagesI;
import com.github.devconslejme.misc.QueueI;
import com.github.devconslejme.misc.QueueI.CallableXAnon;
import com.github.devconslejme.misc.SimulationTimeI;
import com.github.devconslejme.misc.TimeConvertI;
import com.github.devconslejme.misc.TimeFormatI;
import com.github.devconslejme.misc.TimedDelay;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.BulletAppState.ThreadingType;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionGroupListener;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.collision.CollisionResult;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.BatchNode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SimpleBatchNode;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;

/**
 * http://physics.nist.gov/cuu/Units/units.html
 * http://www.bulletphysics.org/mediawiki-1.5.8/index.php?title=Frequently_Asked_Questions
 * so: length meter m, mass kilogram kg, time second s
 * 
 * @author Henrique Abdalla <https://github.com/AquariusPower><https://sourceforge.net/u/teike/profile/>
 */
public class PhysicsI implements PhysicsTickListener, PhysicsCollisionGroupListener, PhysicsCollisionListener{
	public static PhysicsI i(){return GlobalManagerI.i().get(PhysicsI.class);}
	
	private ArrayList<PhysicsData> apdPreventDisintegr = new ArrayList<PhysicsData>();
	
	private ArrayList<PhysicsData> apdDisintegrateAtMainThreadQueue = new ArrayList<PhysicsData>();
	private ArrayList<PhysicsData> apdGravityUpdtMainThreadQueue = new ArrayList<>();
	private ArrayList<PhysicsData> apdLocationUpdtMainThreadQueue = new ArrayList<>();
	private ArrayList<PhysicsData> apdSafeSpotRestoreMainThreadQueue = new ArrayList<>();
	
	private long	lTickCount=0;
	private HashMap<PhysicsRigidBody,PhysicsData> hmDisintegratables=new HashMap<PhysicsRigidBody,PhysicsData>(); 
	private HashMap<PhysicsRigidBody,PhysicsData> hmProjectiles=new HashMap<PhysicsRigidBody,PhysicsData>(); 
	private BulletAppState	bullet;
	private PhysicsSpace	pspace;
	private BoundingBox	bbSpace;
	private LinkedHashMap<String, Info>	hmInfo;
	private TimedDelay tdChkOutOfWorldBoundsDisintegrationAllowed = new TimedDelay(10f).setActive(true);
	private TimedDelay tdSaveSafeSpotRot = new TimedDelay(3f).setActive(true);
	private TimedDelay tdInfo = new TimedDelay(1f).setActive(true);
	private TimedDelay tdDisintegrate = new TimedDelay(10f).setActive(true);
	private PhysicsData	pdLastThrownFromCam;
	private int	iThreadPhysTPS;
	private int	iThreadPhysTickSum;
	private long	lThreadPhysLastCalcTPS;
	private float	fThreadPhysTPF;
	private ArrayList<ImpTorForce> arbcThreadPhysicsPreTickQueue = new ArrayList();
	private boolean	bGlueAllowed=true;
//	private float	fDefaultProjectileMaxLife=2;
	private boolean bDisableCcdToLetCollisionGroupsWork;
	private float fDeflectionAngle=90f;

	
	public static class ImpTorForce{
//		private Spatial	spt;
//		private RigidBodyControl	rbc;
		/** @DefSelfNote dont expose it, this class is a simplifier */
		private PhysicsSpace	ps;
		
		private Vector3f	v3fForce;
		private Vector3f	v3fForceLocation;
		private Vector3f	v3fImpulseRelPos;
		private Vector3f	v3fImpulse;
		private Vector3f	v3fTorque;
		private Vector3f	v3fTorqueImpulse;
		private Float	fImpulseAtSelfDirection;
		private PhysicsData	pd;
		private  Float fImpulseAtSelfDirectionUpwardsDisplacement;
		
//		public Spatial getSpt() {
//			return spt;
//		}
//		public Impulse setSpt(Spatial spt) {
//			this.spt = spt;
//			return this;
//		}
//		public RigidBodyControl getRBC() {
//			return rbc;
//		}
//		public Impulse setRBC(RigidBodyControl rbc) {
//			this.rbc = rbc;
//			return this;
//		}
		
		public PhysicsSpace getPs() {
			return ps;
		}
		
		public Vector3f getForce() {
			return v3fForce;
		}
		public ImpTorForce setForce(Vector3f v3fForce) {
			this.v3fForce = v3fForce;
			return this;
		}
		public Vector3f getForceLocation() {
			return v3fForceLocation;
		}
		public ImpTorForce setForceLocation(Vector3f v3fForceLocation) {
			this.v3fForceLocation = v3fForceLocation;
			return this;
		}
		public Vector3f getImpulse() {
			return v3fImpulse;
		}
		public float getImpulseAtSelfDir() {
			return fImpulseAtSelfDirection;
		}
		/**
		 * 
		 * @param fImpulse
		 * @param fUpwardsDisplacement can be null
		 * @return
		 */
		public ImpTorForce setImpulseAtSelfDir(float fImpulse, Float fUpwardsDisplacement) {
			assert v3fImpulse==null;
			this.fImpulseAtSelfDirection=fImpulse;
			this.fImpulseAtSelfDirectionUpwardsDisplacement=fUpwardsDisplacement;
			return this;
		}
		/**
		 * 
		 * @param v3fImpulse
		 * @param v3fRelPos can be null (will default to zero)
		 * @return
		 */
		public ImpTorForce setImpulse(Vector3f v3fImpulse,Vector3f v3fRelPos) {
			assert fImpulseAtSelfDirection==null;
			this.v3fImpulse = v3fImpulse;
			this.v3fImpulseRelPos = v3fRelPos==null?Vector3f.ZERO:v3fRelPos;
			return this;
		}
		public Vector3f getTorque() {
			return v3fTorque;
		}
		public ImpTorForce setTorque(Vector3f v3fTorque) {
			this.v3fTorque = v3fTorque;
			return this;
		}
		public Vector3f getTorqueImpulse() {
			return v3fTorqueImpulse;
		}
		/**
		 * THIS IS NOT RELATIVE TO THE CURRENT ORIENTATION!!! this is a global orientation thing :/
		 * @param v3fTorqueImpulse
		 * @return
		 */
		public ImpTorForce setTorqueImpulse(Vector3f v3fTorqueImpulse) {
			this.v3fTorqueImpulse = v3fTorqueImpulse;
			return this;
		}
		
	}

	public void configure(){
		bullet = new BulletAppState();
		bullet.setThreadingType(ThreadingType.PARALLEL);
		AppI.i().attatchAppState(bullet);
		
		pspace = bullet.getPhysicsSpace();
		pspace.addTickListener(PhysicsI.this);
		pspace.addCollisionListener(this);
		pspace.addCollisionGroupListener(this, PhysicsCollisionObject.COLLISION_GROUP_01);
		pspace.addCollisionGroupListener(this, PhysicsCollisionObject.COLLISION_GROUP_02);
		pspace.addCollisionGroupListener(this, PhysicsCollisionObject.COLLISION_GROUP_03);
		pspace.addCollisionGroupListener(this, PhysicsCollisionObject.COLLISION_GROUP_04);
		pspace.addCollisionGroupListener(this, PhysicsCollisionObject.COLLISION_GROUP_05);
		pspace.addCollisionGroupListener(this, PhysicsCollisionObject.COLLISION_GROUP_06);
		pspace.addCollisionGroupListener(this, PhysicsCollisionObject.COLLISION_GROUP_07);
		pspace.addCollisionGroupListener(this, PhysicsCollisionObject.COLLISION_GROUP_08);
		pspace.addCollisionGroupListener(this, PhysicsCollisionObject.COLLISION_GROUP_09);
		pspace.addCollisionGroupListener(this, PhysicsCollisionObject.COLLISION_GROUP_10);
		pspace.addCollisionGroupListener(this, PhysicsCollisionObject.COLLISION_GROUP_11);
		pspace.addCollisionGroupListener(this, PhysicsCollisionObject.COLLISION_GROUP_12);
		pspace.addCollisionGroupListener(this, PhysicsCollisionObject.COLLISION_GROUP_13);
		pspace.addCollisionGroupListener(this, PhysicsCollisionObject.COLLISION_GROUP_14);
		pspace.addCollisionGroupListener(this, PhysicsCollisionObject.COLLISION_GROUP_15);
		pspace.addCollisionGroupListener(this, PhysicsCollisionObject.COLLISION_GROUP_16);
		
		bbSpace = new BoundingBox(pspace.getWorldMin(), pspace.getWorldMax());
		
		initMaintenanceUpdateLoop();
//		initUpdateGravity();
	}
	
//	private void initUpdateGravity() {
//		QueueI.i().enqueue(new CallableXAnon() {
//			@Override
//			public Boolean call() {
//				for(PhysicsData pd:apdListGravityUpdtMainThreadQueue) {
//					pd.applyNewGravityAtMainThread();
//				}
//				apdListGravityUpdtMainThreadQueue.clear();
//				return true;
//			}
//		}).enableLoopMode();
//	}
	
	private void initMaintenanceUpdateLoop() {
		hmInfo = new LinkedHashMap<String,Info>();
		
		QueueI.i().enqueue(new CallableXAnon() {
			@Override
			public Boolean call() {
				if(tdInfo.isReady(true))updateInfo();
				
				updateLevitators(pspace,getTPF());
				
				/**
				 * these queues may be harder to maintain than a single one, 
				 * but may help on debugging and to grant their execution order
				 * without having to filter a single list for the right order
				 * 
				 * TODO make such single list auto sorted in the right order?
				 * The ones with timed delay, would then be checked individually, instead of a single td chk 
				 */
				for(PhysicsData pd:apdGravityUpdtMainThreadQueue)pd.applyNewGravityAtMainThread();
				apdGravityUpdtMainThreadQueue.clear();
				for(PhysicsData pd:apdSafeSpotRestoreMainThreadQueue)pd.applyRestoreSafeSpotRotAtMainThread();
				apdSafeSpotRestoreMainThreadQueue.clear();
				for(PhysicsData pd:apdLocationUpdtMainThreadQueue)pd.applyNewPhysLocationAtMainThread();
				apdLocationUpdtMainThreadQueue.clear();
				
				updateProjectiles();  //b4 disintegration!!! (so it may even just disintegrate safely)
				if(tdDisintegrate.isReady(true))updateDisintegratablesAndItsQueue(); //LAST THING
				
				return true;
			}
		}).enableLoopMode();
	}
	
	protected void updateProjectiles() {
		for(PhysicsData pd:hmProjectiles.values()){
			/**
			 * some group collide() (phys thread) will not generate collisions() (main thread)
			 * when looks the projectile is being auto-deflected...
			 */
			PhysicsProjectileI.i().glueProjectileCheckApply(pd,pd.pdGlueWhere,null);
			
			/**
			 * fly direction apply, this cancels rotations, TODO bad for flying rotating knives btw
			 */
			if(pd.isAlignFlyDirection() && pd.pdGlueWhere==null) {
				Vector3f v3fMvDir=pd.getPRB().getLinearVelocity().normalize();
				float fAngleBetweenMoveAndCurrentDeg = pd.getPhysicsRotationCopy().getRotationColumn(2).angleBetween(v3fMvDir)*FastMath.RAD_TO_DEG;
	//			System.out.println("deg="+fAngleBetweenMoveAndCurrentDeg);
				if(fAngleBetweenMoveAndCurrentDeg>10) {
					Spatial spt = pd.getSpatialWithPhysics();
	//				if(spt.getLocalScale().length()<5)spt.setLocalScale(1,1,16);System.out.println("l="+spt.getLocalScale().length());
					spt.lookAt(pd.getPRB().getPhysicsLocation().add(v3fMvDir), Vector3f.UNIT_Y);
					this.syncPhysTransfFromSpt(pd, false, true);
				}
			}
		}
	}
	
	protected void updateDisintegratablesAndItsQueue() {
		for(PhysicsData pd:hmDisintegratables.values()){
			if(!pd.bAllowDisintegration)apdPreventDisintegr.add(pd); //as the disintegration request may happen outside here too
			
			if(pd.getAgeNano() > pd.getProjectileMaxLifeTime() ){
				requestDisintegration(pd);
//				if(!apdDisintegrateAtMainThreadQueue.contains(pd))apdDisintegrateAtMainThreadQueue.add(pd);
			}
//			else{
//				updateIgnoredProjectileCollisions(pd);
//			}
		}
		
		for(PhysicsData pd:apdPreventDisintegr){
			hmDisintegratables.remove(pd.prb);
			apdDisintegrateAtMainThreadQueue.remove(pd);
		}
		apdPreventDisintegr.clear();
		
		for(PhysicsData pd:apdDisintegrateAtMainThreadQueue){
			disintegrate(pd);
		}
		apdDisintegrateAtMainThreadQueue.clear();
	}
	
	protected void disintegrate(PhysicsData pd){
		if(EDebug.LogDisintegrations.b()){
			syso("Disintegrating:"+pd.getInfo()+",");
		}
		
		erasePhysicsFrom(pd.getSpatialWithPhysics());
		
		Node nodeParent = pd.getSpatialWithPhysics().getParent();
		pd.getSpatialWithPhysics().removeFromParent();
		if(nodeParent instanceof BatchNode)((BatchNode)nodeParent).batch();  
		
		pd.bDisintegrated=true;
	}

	public void updateInfo(){
		InfoJmeI.i().putAt(hmInfo,"TPS",iThreadPhysTPS);
		InfoJmeI.i().putAt(hmInfo,"PhysTPF",fThreadPhysTPF,3);
		InfoJmeI.i().putAt(hmInfo,"Disintegratables",hmDisintegratables.size());
		InfoJmeI.i().putAt(hmInfo,"TotRBCs",pspace.getRigidBodyList().size());
		InfoJmeI.i().putAt(hmInfo,"Spd",bullet.getSpeed(),2);
		InfoJmeI.i().putAt(hmInfo,"Grav",pspace.getGravity(new Vector3f()),1);
		InfoJmeI.i().putAt(hmInfo,"Min",pspace.getWorldMin(),0);
		InfoJmeI.i().putAt(hmInfo,"Max",pspace.getWorldMax(),0);
		InfoJmeI.i().putAt(hmInfo,"TotChars",pspace.getCharacterList().size());
		InfoJmeI.i().putAt(hmInfo,"TotGhosts",pspace.getGhostObjectList().size());
		InfoJmeI.i().putAt(hmInfo,"TotVehicles",pspace.getVehicleList().size());
		InfoJmeI.i().putAt(hmInfo,"TotJoints",pspace.getJointList().size());
		
		if(pdLastThrownFromCam!=null){
			InfoJmeI.i().putAt(hmInfo,"LtTrwSpd",pdLastThrownFromCam.imp.fImpulseAtSelfDirection);
		}
		
		HWEnvironmentJmeI.i().putCustomInfo("Phys",InfoJmeI.i().prepareFullInfoRecursive(hmInfo));
		/*
		HWEnvironmentJmeI.i().putCustomInfo("Phys",""
			+"Disintegratables="+hmDisintegratables.size()+", "
			+"Spd="+bullet.getSpeed()+", "
			+"Grav="+ps.getGravity(new Vector3f())+", "
			+"Min="+ps.getWorldMin()+", "
			+"Max="+ps.getWorldMax()+", "
			+"TotChars="+ps.getCharacterList().size()+", "
			+"TotGhosts="+ps.getGhostObjectList().size()+", "
			+"TotRBCs="+ps.getRigidBodyList().size()+", "
			+"TotVehicles="+ps.getVehicleList().size()+", "
			+"TotJoints="+ps.getJointList().size()+", "
			+"LtTrwSpd="+pdLastThrownFromCam.imp.fImpulseAtSelfDirection+", "
		);
		*/
	}
	
	public static class PhysicsData{
		private SimpleBatchNode	sbnGluedProjectiles;
		private long lProjectileMaxLifeTime;
		private boolean	bDisintegrated;
		private Quaternion	quaWRotBkp;
		private BoundingVolume	bv;
		private BoundingBox	bb;
		private CollisionShape	cs;
		private BoundingSphere	bs;
		private PhysicsRigidBody	prb;
		private boolean	bAllowDisintegration=false;
		private Vector3f	v3fLastSafeSpot;
		private Quaternion	quaLastSafeRot;
		private boolean	bTerrain;
		private long	lRestingAtTickCount;
		private boolean	bResting;
		private ImpTorForce	imp;
		private long	lLastPhysUpdateNano;
		private long	lLastRenderUpdateNano;
		private long	lMaterializedSimulTimeNano;
		private Vector3f	v3fWorldGlueSpot;
		private PhysicsData	pdGlueWhere;
		private ArrayList<PhysicsData>	apdPhysicsDataSkipCollisionGroup = new ArrayList<>();
		private boolean	bGlueApplied;
		private boolean	bProjectile;
		private Vector3f	v3fEventCollOtherLocalPos;
		private Vector3f	v3fGlueWherePhysLocalPos;
		private Quaternion	quaGlueWherePhysWRotAtImpact;
		private Vector3f	v3fPosAtPreviousTick;
		private MatterStatus	mts;
		private Geometry	geomOriginalInitialLink;
		private Node	nodexLink;
		private int	iForceAwakePhysTickCount;
		private int	iWaitPhysTicksB4Glueing=1;//1;
		private float	fGrabDist;
//		private CollisionResult cr;
//		private Vector3f v3fGravityBkp;
		private LeviCharacter lcGrabber;
		private Vector3f v3fLevitationDisplacement=null;
		private float fCCdMotionThreshold;
		private Vector3f v3fNewGravity=null;
		private boolean bSuspendLevitation;
		private Vector3f v3fNewPhysLocation;
		public boolean bReadyToGlue;
		private PhysicsData pdLevitationFollow;
		private RayCastResultX resxGlueTarget;
		private boolean bGlueTargetDeflected;
		private float fLastHitAngleAtGluableDeg;
		private boolean bAlignFlyDirection=true;
		
		/**
		 * radians
		 * @param fX
		 * @param fY
		 * @param fZ
		 * @return new final rotation
		 */
		public Quaternion addRotation(float fX,float fY,float fZ){
			Quaternion qua = new Quaternion();
			qua.fromAngles(fX,fY,fZ);
			getPRB().setPhysicsRotation(getPRB().getPhysicsRotation().mult(qua));
			return getPRB().getPhysicsRotation();
		}
		
		public boolean isAlignFlyDirection() {
			return bAlignFlyDirection;
		}

		public long getAgeNano() {
			return (SimulationTimeI.i().getNanoTime() - lMaterializedSimulTimeNano);
		}

		public long getProjectileMaxLifeTime() {
			if(bGlueApplied)return lProjectileMaxLifeTime*PhysicsProjectileI.i().getGluedProjectileMaxLifeTimeMultiplier();
			return lProjectileMaxLifeTime;
		}

		public PhysicsData setProjectileMaxLifeTime(float fSeconds) {
			this.lProjectileMaxLifeTime = TimeConvertI.i().secondsToNano(fSeconds);
			return this; 
		}
		
		public PhysicsData(Node nodex, Geometry geom) {
			this.nodexLink=nodex;
			this.geomOriginalInitialLink=geom;
//			this.v3fGravityBkp = new Vector3f(PhysicsI.i().getGravity());
//			if(spt instanceof NodeX)nodexLink=(NodeX)spt;
//			this.sptLink = spt;
			
			setProjectileMaxLifeTime(PhysicsProjectileI.i().getDefaultProjectileMaxLife());
		}

		public PhysicsRigidBody getPRB() {
			return prb;
		}

		public boolean isAllowDisintegration() {
			return bAllowDisintegration;
		}

		public PhysicsData setAllowDisintegration(boolean bAllowDisintegration) {
			this.bAllowDisintegration = bAllowDisintegration;
			if(bAllowDisintegration) {
				PhysicsI.i().hmDisintegratables.put(prb, this);
			}else {
				PhysicsI.i().hmDisintegratables.remove(prb);
			}
			return this; 
		}

//		public void disintegrateLater() {
//			PhysicsI.i().requestDisintegration(this);
////			apdDisintegrate.add(this);
////			bDisintegrate=true;
//		}

		public Vector3f getLastSafeSpot() {
			return v3fLastSafeSpot;
		}

		public boolean isTerrain() {
			return bTerrain;
		}

		public PhysicsData setTerrain(boolean bTerrain) {
			this.bTerrain = bTerrain;
			return this; 
		}
		
		public void saveSafePosRotFromSpatialLink() {
			saveSafePosRot(getSpatialWithPhysics().getWorldTranslation(), getSpatialWithPhysics().getWorldRotation());
		}

		public boolean isWasSafePosRotSavedAtPreviousTickAndUpdate(long lTickCount){
			boolean b = lTickCount-1 == this.lRestingAtTickCount;
			if(b)this.lRestingAtTickCount=lTickCount;
			return b;
		}
		
		public void saveSafePosRot(Vector3f v3fPos, Quaternion quaRot) {
			saveSafePosRot(-1, v3fPos, quaRot);
		}
		public void saveSafePosRot(long lRestingAtTickCount, Vector3f v3fPos, Quaternion quaRot) {
			this.lRestingAtTickCount = lRestingAtTickCount;
//			if(v3fPos!=null)
			assert v3fPos!=null;
			this.v3fLastSafeSpot=v3fPos.clone();
			
			// rot may not be available
			if(quaRot!=null)this.quaLastSafeRot=quaRot.clone();
		}

		public boolean isResting() {
			return bResting;
		}

		public void setResting(boolean b) {
			this.bResting=b;
		}

		public void setLastImpuse(ImpTorForce imp) {
			this.imp = imp;
		}

		public void updateMaterializedAtTime() {
			this.lMaterializedSimulTimeNano=SimulationTimeI.i().getNanoTime();
		}

		public boolean isExplodeIfHit(PhysicsData pdWhere) {
			return bAllowDisintegration && pdWhere.bAllowDisintegration;
		}

//		public void setGlueWhere(PhysicsData pdWhere) {
//			this.v3fGlueAt = rbc.getPhysicsLocation();
//			this.pdGlueWhere = pdWhere;
//			if(!PhysicsI.i().apdGlue.contains(this))PhysicsI.i().apdGlue.add(this);
//		}

		public PhysicsData getGlueWhere() {
			return pdGlueWhere;
		}
		public void setGlueWhere(PhysicsData pdWhere) {
			this.pdGlueWhere=pdWhere;
		}

		public boolean isProjectile() {
			return bProjectile;
		}
		
		@Override
		public String toString() {
			return getSpatialWithPhysics().getName();
		}

		public String getInfo() {
			return getSpatialWithPhysics().getName()+","+getSpatialWithPhysics().getClass().getSimpleName()+","+prb.getClass().getSimpleName();
		}
		
		public boolean isEnclosed() {
			return nodexLink!=null;
		}
		
		public Spatial getSpatialWithPhysics(){
			if(isEnclosed())return nodexLink;
			return geomOriginalInitialLink;
		}
		
//		public NodeX getEnclosingNode() {
//			return nodexLink;
//		}

//		public Geometry getGeometry() {
//			return geomLink;
//		}

		public void setMatterStatus(MatterStatus mts) {
//			if(mt!=null){
//				mts=new MatterStatus(mt);
//			}else{
//				mts=new MatterStatus(EMatter.Generic1KgPerM3.get());
//			}
			this.mts=mts;
			if(this.mts.getMassGrams()==0) {
				/**
				 * only uses the volume if the mass is not set,
				 * this way, a mass can override the volume,
				 * making it not real tho.
				 * 
				 * good to prevent too tiny physics colliders, 
				 * and to prevent physics engine problems derived of too discrepant masses (one too high and other too low)
				 * TODO confirm, the good mass ratio for collisions should not exceed 20??? (like 1kg vs 20kg), or just check that and reduce applied forces while creating damaged spots on both colliders?
				 */
				this.mts.setVolumeM3(bv.getVolume());
			}
			prb.setMass((float)this.mts.getMassKg());
		}

		public Geometry getInitialOriginalGeometry() {
			return geomOriginalInitialLink;
		}

		public void forceAwakeSomeTicks() {
			iForceAwakePhysTickCount=10;
		}

		public boolean isStatic() {
			return prb.getMass()==0;
		}

		public boolean isActivatable() {
			return ActivatorI.i().isActivetable(getInitialOriginalGeometry());
		}

		public PhysicsData setGrabDist(float fGrabDist) {
			this.fGrabDist = fGrabDist;
			return this;
		}
		
		public float getGrabDist(){
			return fGrabDist;
		}
		
		public void restoreSafeSpotRotAtMainThread() {
			if(MainThreadI.i().isCurrentMainThread()) {
				applyRestoreSafeSpotRotAtMainThread();
			}else {
				PhysicsI.i().apdSafeSpotRestoreMainThreadQueue.add(this);
			}
		}
		public void applyRestoreSafeSpotRotAtMainThread() {
			MainThreadI.i().assertEqualsCurrentThread();
			PhysicsI.i().resetForces(this);
			prb.setPhysicsLocation(v3fLastSafeSpot);
			prb.setPhysicsRotation(quaLastSafeRot);
		}
		/**
		 * bullet native crashes otherwise..
		 */
		public void applyNewGravityAtMainThread() {
			MainThreadI.i().assertEqualsCurrentThread();
			this.prb.setGravity(v3fNewGravity);
		}
		/**
		 * @param v3f null to restore bkp
		 */
		public void setNewGravityAtMainThread(Vector3f v3f) {
			if(v3f==null)v3f = PhysicsI.i().getGravityCopy().clone(); //restore the bkp
			if(v3fNewGravity==null || !v3fNewGravity.equals(v3f)) {
				v3fNewGravity = v3f.clone();
				if(MainThreadI.i().isCurrentMainThread()) {
					applyNewGravityAtMainThread();
				}else {
					PhysicsI.i().apdGravityUpdtMainThreadQueue.add(this);
				}
			}
		}
		public PhysicsData setTempGravityTowards(Vector3f v3fGravityTargetSpot, Float fAcceleration) {
			Vector3f v3fNewGravity=null;
			if(v3fGravityTargetSpot==null) {
				v3fNewGravity=(PhysicsI.i().getGravityCopy());
			}else {
				v3fNewGravity = v3fGravityTargetSpot.subtract(prb.getPhysicsLocation()).normalize();
				v3fNewGravity.multLocal(fAcceleration!=null?fAcceleration:PhysicsI.i().getGravityCopy().length());
			}
			
			setNewGravityAtMainThread(v3fNewGravity);
			prb.activate();
			
			return this;
		}
		/**
		 * otherwise, will glitch impulses applied to it, making them inverted in rotation/torque! 
		 */
		public void setPhysicsLocationAtMainThread(Vector3f v3f) {
			v3fNewPhysLocation=v3f.clone();
			if(MainThreadI.i().isCurrentMainThread()) {
				applyNewPhysLocationAtMainThread();
			}else {
				PhysicsI.i().apdLocationUpdtMainThreadQueue.add(this);
			}
		}

		private void applyNewPhysLocationAtMainThread() {
			MainThreadI.i().assertEqualsCurrentThread();
			this.prb.setPhysicsLocation(v3fNewPhysLocation);
		}

		public void setPRB(PhysicsRigidBody prb) {
			this.prb=prb;
		}

		public PhysicsData setGrabbedBy(LeviCharacter bccxGrabber) {
			this.lcGrabber = bccxGrabber;
			return this;
		}
//		public PhysicsData setGrabbedBy(BetterCharacterControlX bccxGrabber) {
//			this.bccxGrabber = bccxGrabber;
//			return this;
//		}
		
		public boolean isGrabbed() {
			return lcGrabber!=null;
		}

//		public CollisionResult getCollisionResult() {
//			return cr;
//		}
//
//		public PhysicsData setCollisionResult(CollisionResult cr) {
//			this.cr = cr;
//			return this; 
//		}

		public SimpleBatchNode getSBNodeGluedProjectiles() {
			return sbnGluedProjectiles;
		}

		public PhysicsData setSBNodeGluedProjectiles(SimpleBatchNode sbnGluedProjectiles) {
			this.sbnGluedProjectiles = sbnGluedProjectiles;
			return this; 
		}

		public PhysicsData setProjectileMaxLifeTime(long lProjectileMaxLifeTime) {
			this.lProjectileMaxLifeTime = lProjectileMaxLifeTime;
			return this; 
		}

		public boolean isDisintegrated() {
			return bDisintegrated;
		}

		public PhysicsData setDisintegrated(boolean bDisintegrated) {
			this.bDisintegrated = bDisintegrated;
			return this;
		}

		public Quaternion getWRotBkp() {
			return quaWRotBkp;
		}

		public PhysicsData setWRotBkp(Quaternion quaWRotBkp) {
			this.quaWRotBkp = quaWRotBkp;
			return this;
		}

		public BoundingVolume getBoundingVolume() {
			return bv;
		}

		public PhysicsData setBoundingVolume(BoundingVolume bv) {
			this.bv = bv;
			return this;
		}

		public BoundingBox getBoundingBox() {
			return bb;
		}

		public PhysicsData setBoundingBox(BoundingBox bb) {
			this.bb = bb;
			return this;
		}

		public CollisionShape getCollisionShape() {
			return cs;
		}

		public PhysicsData setCollisionShape(CollisionShape cs) {
			this.cs = cs;
			return this;
		}

		public BoundingSphere getBoundingSphere() {
			return bs;
		}

		public PhysicsData setBoundingSphere(BoundingSphere bs) {
			this.bs = bs;
			return this;
		}

		public boolean isbAllowDisintegration() {
			return bAllowDisintegration;
		}

		public PhysicsData setbAllowDisintegration(boolean bAllowDisintegration) {
			this.bAllowDisintegration = bAllowDisintegration;
			return this;
		}

		public Vector3f getV3fLastSafeSpot() {
			return v3fLastSafeSpot;
		}

		public PhysicsData setV3fLastSafeSpot(Vector3f v3fLastSafeSpot) {
			this.v3fLastSafeSpot = v3fLastSafeSpot;
			return this;
		}

		public Quaternion getQuaLastSafeRot() {
			return quaLastSafeRot;
		}

		public PhysicsData setQuaLastSafeRot(Quaternion quaLastSafeRot) {
			this.quaLastSafeRot = quaLastSafeRot;
			return this;
		}

		public boolean isbTerrain() {
			return bTerrain;
		}

		public PhysicsData setbTerrain(boolean bTerrain) {
			this.bTerrain = bTerrain;
			return this;
		}

		public long getlRestingAtTickCount() {
			return lRestingAtTickCount;
		}

		public PhysicsData setlRestingAtTickCount(long lRestingAtTickCount) {
			this.lRestingAtTickCount = lRestingAtTickCount;
			return this;
		}

		public boolean isbResting() {
			return bResting;
		}

		public PhysicsData setbResting(boolean bResting) {
			this.bResting = bResting;
			return this;
		}

		public ImpTorForce getImp() {
			return imp;
		}

		public PhysicsData setImp(ImpTorForce imp) {
			this.imp = imp;
			return this;
		}

		public long getLastPhysUpdateNano() {
			return lLastPhysUpdateNano;
		}

		public PhysicsData setlLastPhysUpdateNano(long lLastPhysUpdateNano) {
			this.lLastPhysUpdateNano = lLastPhysUpdateNano;
			return this;
		}

		public long getlLastRenderUpdateNano() {
			return lLastRenderUpdateNano;
		}

		public PhysicsData setlLastRenderUpdateNano(long lLastRenderUpdateNano) {
			this.lLastRenderUpdateNano = lLastRenderUpdateNano;
			return this;
		}

		public long getlMaterializedSTime() {
			return lMaterializedSimulTimeNano;
		}

		public PhysicsData setlMaterializedSTime(long lMaterializedSTime) {
			this.lMaterializedSimulTimeNano = lMaterializedSTime;
			return this;
		}

		public Vector3f getV3fWorldGlueSpot() {
			return v3fWorldGlueSpot;
		}

		public PhysicsData setV3fWorldGlueSpot(Vector3f v3fWorldGlueSpot) {
			this.v3fWorldGlueSpot = v3fWorldGlueSpot;
			return this;
		}

		public boolean containsPhysicsDataSkipCollisionGroup(PhysicsData pd) {
			return apdPhysicsDataSkipCollisionGroup.contains(pd);
		}

		public void addPhysicsDataSkipCollisionGroup(PhysicsData pd) {
			this.apdPhysicsDataSkipCollisionGroup.add(pd);
		}

		public boolean isbGlueApplied() {
			return bGlueApplied;
		}

		public PhysicsData setbGlueApplied(boolean bGlueApplied) {
			this.bGlueApplied = bGlueApplied;
			return this;
		}

		public boolean isbProjectile() {
			return bProjectile;
		}

		public PhysicsData setProjectile(boolean bProjectile) {
			this.bProjectile = bProjectile;
			if(bProjectile) {
				PhysicsI.i().hmProjectiles.put(prb, this);
			}else {
				PhysicsI.i().hmProjectiles.remove(prb);
			}
			return this;
		}

		public Vector3f getV3fEventCollOtherLocalPos() {
			return v3fEventCollOtherLocalPos;
		}

		public PhysicsData setV3fEventCollOtherLocalPos(Vector3f v3fEventCollOtherLocalPos) {
			this.v3fEventCollOtherLocalPos = v3fEventCollOtherLocalPos;
			return this;
		}

		public Vector3f getV3fGlueWherePhysLocalPos() {
			return v3fGlueWherePhysLocalPos;
		}

		public PhysicsData setV3fGlueWherePhysLocalPos(Vector3f v3fGlueWherePhysLocalPos) {
			this.v3fGlueWherePhysLocalPos = v3fGlueWherePhysLocalPos;
			return this;
		}

		public Quaternion getQuaGlueWherePhysWRotAtImpact() {
			return quaGlueWherePhysWRotAtImpact;
		}

		public PhysicsData setQuaGlueWherePhysWRotAtImpact(
			Quaternion quaGlueWherePhysWRotAtImpact) {
			this.quaGlueWherePhysWRotAtImpact = quaGlueWherePhysWRotAtImpact;
			return this;
		}

		public Vector3f getV3fPosAtPreviousTick() {
			return v3fPosAtPreviousTick;
		}

		public PhysicsData setV3fPosAtPreviousTick(Vector3f v3fPosAtPreviousTick) {
			this.v3fPosAtPreviousTick = v3fPosAtPreviousTick;
			return this;
		}

		public MatterStatus getMts() {
			return mts;
		}

//		public PhysicsData setMatterStatus(MatterStatus mts) {
//			this.mts = mts;
//			return this;
//		}

		public Geometry getGeomOriginalInitialLink() {
			return geomOriginalInitialLink;
		}

		public PhysicsData setGeomOriginalInitialLink(Geometry geomOriginalInitialLink) {
			this.geomOriginalInitialLink = geomOriginalInitialLink;
			return this;
		}

		public Node getNodexLink() {
			return nodexLink;
		}

		public PhysicsData setNodexLink(Node nodexLink) {
			this.nodexLink = nodexLink;
			return this;
		}

		public int getiForceAwakePhysTickCount() {
			return iForceAwakePhysTickCount;
		}

		public PhysicsData setiForceAwakePhysTickCount(int iForceAwakePhysTickCount) {
			this.iForceAwakePhysTickCount = iForceAwakePhysTickCount;
			return this;
		}

		public int getiForceStaticPhysTickCount() {
			return iWaitPhysTicksB4Glueing;
		}

		public PhysicsData setiForceStaticPhysTickCount(int iForceStaticPhysTickCount) {
			this.iWaitPhysTicksB4Glueing = iForceStaticPhysTickCount;
			return this;
		}

//		public Vector3f getV3fGravityBkp() {
//			return v3fGravityBkp;
//		}

		public LeviCharacter getGrabber() {
			return lcGrabber;
		}

		public boolean isLevitating() {
			if(bSuspendLevitation)return false;
			return v3fLevitationDisplacement!=null;
		}
		public Float getLevitationHeight() {
			return v3fLevitationDisplacement.y;
		}
		
		/**
		 * 
		 * @param pdFollow can be null, otherwise will be a reference for the height
		 * @param fLevitationHeight
		 * @return
		 */
		public PhysicsData setLevitation(PhysicsData pdLevitationFollow,Float fLevitationHeight) {
			setLevitation(pdLevitationFollow, new Vector3f(0,fLevitationHeight,0));
			return this;
		}
		
		/**
		 * 
		 * @param pdLevitationFollow
		 * @param v3fLeviDispl it will be relative to where the followed is looking at, including it's up vector
		 * @return
		 */
		public PhysicsData setLevitation(PhysicsData pdLevitationFollow,Vector3f v3fLeviDispl) {
			this.pdLevitationFollow=pdLevitationFollow;
			this.v3fLevitationDisplacement = v3fLeviDispl!=null ? v3fLeviDispl.clone() : null;
			if(this.v3fLevitationDisplacement==null)setNewGravityAtMainThread(null);
			return this; 
		}

		public void setCcdMotionThresholdBkp(float fCCdMotionThreshold) {
			this.fCCdMotionThreshold=fCCdMotionThreshold;
		}
		public float getCCdMotionThresholdBkp() {
			return fCCdMotionThreshold;
		}

		public void suspendLevitationIfItIs() {
			bSuspendLevitation=true;
		}

		public void resumeLevitationIfItWas() {
			bSuspendLevitation=false;
		}

		public boolean isReadyToGlue() {
			return bReadyToGlue;
		}

		public Quaternion getPhysicsRotationCopy() {
			return prb.getPhysicsRotation(); // is a copy already
		}

		public PhysicsData getLeviFollow() {
			return pdLevitationFollow;
		}

		public boolean isGrabbedBy(PhysicsData pdLevi) {
			return lcGrabber!=null && lcGrabber.pdTorso==pdLevi;
		}

		public float getMass() {
			return prb.getMass();
		}

		public boolean checkGluedAt(RayCastResultX resx) {
			// TODO use info like target surface hardness, projectile piercability, projectile shape/pointyness, impact energy/velocity etc
			Vector3f v3fHitDir = prb.getPhysicsRotation().getRotationColumn(2);
			fLastHitAngleAtGluableDeg = resx.getNormal().angleBetween(v3fHitDir.negate()) * FastMath.RAD_TO_DEG;
			fLastHitAngleAtGluableDeg = 90f-fLastHitAngleAtGluableDeg;
			boolean bDeflected = fLastHitAngleAtGluableDeg < PhysicsI.i().getDefaultDeflectionAngle();
			
			if(!bDeflected) {
				this.resxGlueTarget=resx;
				
				//easifiers
				pdGlueWhere=(resx.pd);
				
				v3fWorldGlueSpot=resx.v3fWrldHit;
				
				v3fGlueWherePhysLocalPos=resx.v3fWrldHit.subtract(resx.pd.prb.getPhysicsLocation());
				quaGlueWherePhysWRotAtImpact=resx.pd.prb.getPhysicsRotation();
			}		
			
			return !bDeflected;
		}
		
		@Deprecated
		public boolean isHasGlueTargetDeflected() {
			return bGlueTargetDeflected;
		}

	}
	
	
//	ArrayList<PhysicsData> apdGlue = new ArrayList<PhysicsData>();
	
	synchronized public void requestDisintegration(PhysicsData pd){
		if(!apdDisintegrateAtMainThreadQueue.contains(pd))apdDisintegrateAtMainThreadQueue.add(pd);
	}
	
//	/**
//	 * 
//	 * @param spt
//	 * @return dynamic: mass 1f
//	 */
//	public PhysicsData imbueFromWBounds(Geometry geom){
//		return imbueFromWBounds(geom,null,new Node());
//	}
	/**
	 * TODO create a compound if it is a node with more than one geometry.
	 * @param geom
	 * @param mt
	 * @return
	 */
	public PhysicsData imbueFromWBounds(Geometry geom, MatterStatus mts, Node nodeStore){//, Vector3f v3fForceScaleCS){
		assert !UserDataI.i().contains(geom, PhysicsData.class);
		
		if(nodeStore!=null){
			/** to be on a node is important to let other things be attached to it like stuck projectiles */
			MiscJmeI.i().addToName(nodeStore, "PhysImbued:"+geom.getName(), false);
			
			Transform trf = geom.getWorldTransform();
			geom.setLocalTransform(new Transform());
			nodeStore.setLocalTransform(trf);
			
			Node nodeParent = geom.getParent();
			nodeStore.attachChild(geom);
			if(nodeParent!=null)nodeParent.attachChild(nodeStore);
		}
		
		PhysicsData pd = new PhysicsData(nodeStore,geom);
//		pd.nodexLink=spt;
		pd.saveSafePosRotFromSpatialLink();
		
		/*****************************************
		 * retrieve correct(default/Original/Aligned) bounding
		 */
		//bkp rot
		pd.quaWRotBkp = geom.getWorldRotation().clone();
		// reset rot: look at z+1 from where it is, and up to y=1
		geom.lookAt(geom.getWorldTranslation().add(0,0,1), Vector3f.UNIT_Y);
		
		/**
		 * get the bound related to an unmodified rotation 
		 */
		pd.bv = geom.getWorldBound().clone(); //the world bound will already be a scaled result...
		
		//restore rot
		geom.lookAt(geom.getWorldTranslation().add(pd.quaWRotBkp.getRotationColumn(2)), pd.quaWRotBkp.getRotationColumn(1));
		
		/***********************************************
		 *  create collision shape from bounds
		 */
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
			throw new DetailedException("unsupported "+pd.bv.getClass(),geom);
		}
		
		pd.setPRB(new RigidBodyControl(pd.cs));
		
		pd.setMatterStatus(mts);
//		if(mt!=null){
//			pd.mts=new MatterStatus(mt);
//		}else{
//			pd.mts=new MatterStatus(EMatter.Generic1KgPerM3.get());
//		}
//		pd.mts.setVolumeM3(pd.bv.getVolume());
//		pd.rbc.setMass((float) pd.mts.getMassKg());
		
		float fCCdMotionThreshold = fPseudoDiameter/2f;
		pd.setCcdMotionThresholdBkp(fCCdMotionThreshold);
		pd.prb.setCcdMotionThreshold(fCCdMotionThreshold);
		if(isDisableCcdToLetCollisionGroupsWork()) {
			disableCcdToLetCollisionGroupsWork(pd);
		}
		
		/**
		 * "Each time the object moves more than (motionThreshold) within one frame a sphere of radius 
		 * (sweptSphereRadius) is swept from the first position of the object to the position in the 
		 * next frame to check if there was any objects in between that were missed because the object 
		 * moved too fast." - https://hub.jmonkeyengine.org/t/ccd-usage/24655/13
		 * 
		 * "The radius is just the radius of the sphere that is swept to do this check, so make it so 
		 * large that it resembles your object." - https://hub.jmonkeyengine.org/t/ccd-usage/24655/2
		 */
		pd.prb.setCcdSweptSphereRadius(fPseudoDiameter/2f);
		
		pd.getSpatialWithPhysics().addControl((RigidBodyControl)pd.prb); //this will put the rbc at spatial's W/L location/rotation
		
		pd.v3fPosAtPreviousTick=pd.getSpatialWithPhysics().getLocalTranslation().clone();
		
		pd.updateMaterializedAtTime();
		
		UserDataI.i().putSafelyMustNotExist(pd.getSpatialWithPhysics(), pd); //BEFORE adding to phys space as its thread will be trying to retrieve it!
		pspace.add(pd.getSpatialWithPhysics()); //LAST THING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
		
		return pd;
	}
	
	/**
	 * collision groups skippers/ignorers/collisioDeniers will not work without this... :(
	 * @param pd
	 */
	@Workaround
	public void disableCcdToLetCollisionGroupsWork(PhysicsData pd) {
		pd.getPRB().setCcdMotionThreshold(0);
	}
	
	public PhysicsData getPhysicsDataFrom(PhysicsCollisionObject pco){
		Spatial spt=(Spatial)pco.getUserObject();
		PhysicsData pd = getPhysicsDataFrom(spt);
		return pd;
	}
	public PhysicsData getPhysicsDataFrom(Spatial spt){
		PhysicsData pd = UserDataI.i().getMustExistOrNull(spt, PhysicsData.class);
		if(pd==null){
			/**
			 * includes last to work even if detached, the top root will have no physics anyway
			 * this also allows childs with different phys data, like stuk projectiles
			 */
			for(Node node:SpatialHierarchyI.i().getAllParents(spt,true)){
				pd = UserDataI.i().getMustExistOrNull(node, PhysicsData.class);
//				if(pd!=null)return pd;
				break;
			}
		}
		return pd;
	}
	
	public void changeMatter(PhysicsData pd, Matter mt, Float fForceMassGramsIgnoringModelBoundVolume) {
		MatterStatus mtsNew = new MatterStatus(mt);
		mtsNew.setVolumeCm3(pd.getMts().getVolumeCm3());
		if(fForceMassGramsIgnoringModelBoundVolume!=null) {
			mtsNew.setMassGrams(fForceMassGramsIgnoringModelBoundVolume);
		}
		pd.setMatterStatus(mtsNew);
		pd.getPRB().setMass((float) mtsNew.getMassKg());
	}
	
	public void putPhysicsDataInfo(Spatial sptSourceRetrieveFrom, HashMap<String,Info> hmStore){
		PhysicsData pd = getPhysicsDataFrom(sptSourceRetrieveFrom);
		if(pd!=null){
			InfoJmeI.i().putAt(hmStore,"mass",pd.mts.getMassKg(),3);
			InfoJmeI.i().putAt(hmStore,"vol",pd.mts.getVolumeM3(),3);
			InfoJmeI.i().putAt(hmStore,"grav",pd.prb.getGravity(),1);
			InfoJmeI.i().putAt(hmStore,"rest",pd.isResting());
			if(pd.getSBNodeGluedProjectiles()!=null)InfoJmeI.i().putAt(hmStore,"GluePrjc",pd.getSBNodeGluedProjectiles().getChildren().size());
			
			// last as may change too much
			InfoJmeI.i().putAt(hmStore,"spd",pd.prb.getLinearVelocity(),2);
			InfoJmeI.i().putAt(hmStore,"angv",pd.prb.getAngularVelocity(),1);
//			InfoJmeI.i().putAt(hmStore,"LtTrwSpd",pd.imp.fImpulseAtSelfDirection);
		}
	}
	
//	private void initUpdateLastTargetInfo() {
//		QueueI.i().enqueue(new CallableXAnon() {
//			@Override
//			public Boolean call() {
//				TargetGeom tgt = TargetI.i().getLastSingleTarget();
//				if(tgt!=null){
//				}
//				return true;
//			}
//		}).enableLoopMode().setDelaySeconds(0.5f);
//	}
	
	public PhysicsData erasePhysicsFrom(Spatial spt){
		RigidBodyControl rbc = spt.getControl(RigidBodyControl.class);
		
		if(hmDisintegratables.containsKey(rbc)){
			hmDisintegratables.remove(rbc);
		}
		
		if(hmProjectiles.containsKey(rbc)){
			hmProjectiles.remove(rbc);
		}
		
		removeFromPhysicsSpace(spt);
		spt.removeControl(rbc); //AFTER from space removal
		
		PhysicsData pd = getPhysicsDataFrom(spt);
		UserDataI.i().eraseAllOf(spt,pd);
		
		return pd;
	}
	
	public void removeFromPhysicsSpace(Spatial spt){
		pspace.remove(spt);
	}
	
	public void add(Spatial spt){
		pspace.add(spt);
	}
	
//	public void applyImpulseLater(Spatial spt, Impulse imp){
//		assert getPhysicsDataFrom(spt)!=null;
////		ps.enqueue(callable)
//		
//		imp.spt = spt;//(Spatial)obj;
//		imp.rbc = imp.spt.getControl(RigidBodyControl.class);
//		imp.ps=ps;
//		
//		synchronized (arbcThreadPhysicsPreTickQueue) {
//			arbcThreadPhysicsPreTickQueue.add(imp);
//		}
//	}
	public void applyImpulseLater(PhysicsData pd, ImpTorForce imp){
//		assert getPhysicsDataFrom(spt)!=null;
//		ps.enqueue(callable)
		
//		imp.spt = spt;//(Spatial)obj;
//		imp.rbc = imp.spt.getControl(RigidBodyControl.class);
		imp.pd=pd;
		imp.ps=pspace;
		
		synchronized (arbcThreadPhysicsPreTickQueue) {
			arbcThreadPhysicsPreTickQueue.add(imp);
		}
	}
	
	/**
	 * THIS IS ANOTHER THREAD
	 */
	@NotMainThread
	@Deprecated
	@Override
	public void prePhysicsTick(PhysicsSpace ps, float tpf) {
//		if(DetailedException.isExitRequested())return;
		try{threadPhysicsPreTick(ps,tpf);}catch(Exception ex){DetailedException.forceExitTrapIrrevocablySuspendCurrentThread(ex);}
	}
	@NotMainThread
	public void threadPhysicsPreTick(PhysicsSpace ps, float tpf) {
		/**
		 * apply impulses/forces requests queued
		 */
		synchronized(arbcThreadPhysicsPreTickQueue){
			for(ImpTorForce imp:arbcThreadPhysicsPreTickQueue){
				assert imp.ps==ps;
				
				if(imp.v3fForce!=null){
					if(imp.v3fForceLocation==null){
						imp.pd.prb.applyCentralForce(imp.v3fForce);
					}else{
						imp.pd.prb.applyForce(imp.v3fForce, imp.v3fForceLocation);
					}
				}
				
				if(imp.fImpulseAtSelfDirection!=null){
					Vector3f v3fDispl = Vector3f.ZERO;
					
					if(imp.fImpulseAtSelfDirectionUpwardsDisplacement!=null) {
						v3fDispl = imp.pd.prb.getPhysicsRotation().getRotationColumn(1).mult(imp.fImpulseAtSelfDirectionUpwardsDisplacement);
					}
					
					imp.pd.prb.applyImpulse(
						imp.pd.prb.getPhysicsRotation().getRotationColumn(2).mult(imp.fImpulseAtSelfDirection), 
						v3fDispl
					);
				}
				
				if(imp.v3fImpulse!=null)
					imp.pd.prb.applyImpulse(imp.v3fImpulse, imp.v3fImpulseRelPos);
				
				if(imp.v3fTorque!=null)
					imp.pd.prb.applyTorque(imp.v3fTorque);
				
				if(imp.v3fTorqueImpulse!=null)
					imp.pd.prb.applyTorqueImpulse(imp.v3fTorqueImpulse);
				
			}
			
			arbcThreadPhysicsPreTickQueue.clear();
		}
		
//		for(PhysicsRigidBody prb:ps.getRigidBodyList()){
//			PhysicsData pd = getPhysicsDataFrom(prb);
//			if(pd!=null && pd.pdGlueWhere!=null)pd.rbc.setMass(0);
//		}
	}
	
	/**
	 * THIS IS ANOTHER THREAD
	 */
	@NotMainThread
	@Deprecated
	@Override
	public void physicsTick(PhysicsSpace ps, float tpf) {
//		if(DetailedException.isExitRequested())return;
		try{threadPhysicsTick(ps,tpf);}catch(Exception ex){DetailedException.forceExitTrapIrrevocablySuspendCurrentThread(ex);}
	}
	/** 
	 * just for naming clarity 
	 * TODO write current forces to spatials for easy access?
	 */
	@NotMainThread
	public void threadPhysicsTick(PhysicsSpace ps, float tpf) {
		threadPhysicsTickCalcTPS(ps,tpf);
		threadPhysicsTickDebugLogDisplacementPerTick(ps,tpf);
		threadPhysicsTickSaveSafeSpot(ps,tpf);
		threadPhysicsTickDisintegrateAtWBoundsOrRestoreToSafeSpot(ps,tpf);
//		threadPhysicsTickLevitators(ps,tpf);
	}
	
//	private void threadPhysicsTickLevitators(PhysicsSpace ps, float tpf) {
	protected void updateLevitators(PhysicsSpace ps, float tpf) {
		for(PhysicsRigidBody prb:ps.getRigidBodyList()){ //TODO create a pd list of levitators instead of check all prbs...
			PhysicsData pdLevi = getPhysicsDataFrom(prb);
			if(!pdLevi.isLevitating())continue;
			
			ArrayList<RayCastResultX> resultsx = rayCastSortNearest(
				prb.getPhysicsLocation(), 
				prb.getPhysicsLocation().add(0,-pdLevi.getLevitationHeight(),0), false, 
				true, true, pdLevi
			);
			
//			RayCastResultX resHitBelow = (resultsx.size()>0) ? resultsx.get(0) : null;
			RayCastResultX resHitBelow = null;
			for(RayCastResultX r:resultsx) { 
				if(r.getPD().getLeviFollow()==pdLevi)continue; //ignore the followers
				if(r.getPD().isGrabbedBy(pdLevi))continue; //ignored grabbeds, prevents levitation trick/glitch TODO allow this optionally as a funny trick/magic
//				if(r.getPd().isGrabbed())continue; //TODO allow this by applying a downwards force on the grabber?
				resHitBelow=r;
				break;
			}
			
			if((pdLevi.getLeviFollow()!=null || resHitBelow!=null) && prb.getGravity().length()>0) {
				pdLevi.setNewGravityAtMainThread(Vector3f.ZERO);
			}
			
			if(resHitBelow==null){
				pdLevi.setNewGravityAtMainThread(null); //restores default gravity, ex.: so it can fall
			}
			
			PhysicsData pdFollow = pdLevi.getLeviFollow();
			if(pdFollow!=null) {
				PhysicsRigidBody prbFollow = pdFollow.getPRB();
				Quaternion quaFollow = prbFollow.getPhysicsRotation();
				Vector3f v3fDisplOrientedFollowTo = prbFollow.getPhysicsLocation();
				Vector3f v3fDispl = pdLevi.v3fLevitationDisplacement;
				v3fDisplOrientedFollowTo.addLocal(quaFollow.getRotationColumn(0).mult(v3fDispl.x));
				v3fDisplOrientedFollowTo.addLocal(quaFollow.getRotationColumn(1).mult(v3fDispl.y));
				v3fDisplOrientedFollowTo.addLocal(quaFollow.getRotationColumn(2).mult(v3fDispl.z));
				
				prb.setPhysicsRotation(prbFollow.getPhysicsRotation());
				prb.setPhysicsLocation(v3fDisplOrientedFollowTo);
			}else
			if(resHitBelow!=null) {
				// positioning above
				float fDistCurrent = prb.getPhysicsLocation().subtract(resHitBelow.getWHitPos()).length();
				float fDistRemainingToReach = pdLevi.getLevitationHeight()-fDistCurrent;
				float fNearMargin = (pdLevi.getLevitationHeight()*0.05f);
				if(fDistRemainingToReach > fNearMargin) {
					float fFinalHeight = pdLevi.getLevitationHeight()-fNearMargin;
					pdLevi.setPhysicsLocationAtMainThread(resHitBelow.getWHitPos().add(0,fFinalHeight,0));
				}
				
				/**
				 * for pseudo levitators (beings with legs): 
				 * use some trick to let the weight of the levitator be applied on below objects
				 * may be an invisible sphere, with the same mass of the levitator and its followers, 
				 * that keeps endless falling where the levitator is?  
				 */
				applyImpulseLater(resHitBelow.getPD(), new ImpTorForce().setImpulse(
					new Vector3f(0,-resHitBelow.getPD().getMass(),0), resHitBelow.getLocalHitPos()));
				
			}
			
		}
	}
	
	public RayCastResultX applyLevitationAtCamTarget(Float fHeight) {
		RayCastResultX pdrtr = getPhysicsDataAtCamDir(false, true);
		if(pdrtr!=null && !pdrtr.pd.isTerrain() && pdrtr.pd.getPRB().getMass()>0) {
			pdrtr.pd.setLevitation(null,fHeight);
		}
		return pdrtr;
	}
	
	public void applyLevitation(PhysicsData pd,Float fHeight) {
		pd.setLevitation(null,fHeight);
	}
	
	private void threadPhysicsTickDisintegrateAtWBoundsOrRestoreToSafeSpot(PhysicsSpace ps, float tpf) {
		// auto disintegrate at world bounds or restore last safe spot TODO do one check per frame instead of all at a delayed time?
		if(tdChkOutOfWorldBoundsDisintegrationAllowed.isReady(true)){
			for(PhysicsRigidBody prb:ps.getRigidBodyList()){
				if(!bbSpace.contains(prb.getPhysicsLocation())){
//					if(CharacterI.i().isCharacter(prb))continue;
//					if(prb.getUserObject() instanceof GeometryTestProjectile)continue;
					
					if(EDebug.TestDynamicPhysicsWithoutSpatialAndData.b() && prb.getUserObject()==null)continue;
					PhysicsData pd = getPhysicsDataFrom(prb);
					if(pd==null)continue;//other stuff
//					if(pd==null){
//						syso("breakpoint here");
//					}
					
					if(pd.isAllowDisintegration()){
						if(EDebug.LogDisintegrationByOutOfWorldBoundaries.b())syso(pd.getInfo()+","+prb.getPhysicsLocation());
						requestDisintegration(pd);
					}else{
						if(pd.getLastSafeSpot()!=null){
//							resetForces(pd);
//							pd.restoreSafeSpotRot();
							pd.restoreSafeSpotRotAtMainThread();
						}
					}
				}
			}
		}
	}

	private void threadPhysicsTickSaveSafeSpot(PhysicsSpace ps, float tpf) {
		// save safe spot
		lTickCount++;
		for(PhysicsRigidBody prb:ps.getRigidBodyList()){
			if(EDebug.TestDynamicPhysicsWithoutSpatialAndData.b() && prb.getUserObject()==null)continue;
			PhysicsData pd = getPhysicsDataFrom(prb);
			if(pd==null)continue; //other stuff
			
			pd.lLastPhysUpdateNano=System.nanoTime();
			
//			if(pd.getPRB().getGravity().length()==0)prb.activate();
			if(prb.getGravity().length()==0)prb.activate(); //no gravity, never rests
			if((pd.iForceAwakePhysTickCount--)>0)prb.activate();
			
			if(pd.isProjectile()){
				if(pd.pdGlueWhere!=null){
					/**
					 * not imediate to let the collision impulse/force be applied on what was hit
					 */
					if(pd.iWaitPhysTicksB4Glueing<=0){
						pd.bReadyToGlue=true;
					}
					pd.iWaitPhysTicksB4Glueing--; //after the check
					
//					if(pd.bGlueApplied){ //not imediate to let the collision impulse/force be applied on what was hit
//						pd.prb.setMass(0f);
//					}else{
					if(!pd.bGlueApplied){ 
						pd.pdGlueWhere.forceAwakeSomeTicks();
					}
				}
				continue;
			}
			
			if(isResting(prb)){
//				if(!pd.isWasSafePosRotSavedAtPreviousTickAndUpdate(lTickCount)){
				if(!pd.isResting()){
					pd.saveSafePosRot(lTickCount, prb.getPhysicsLocation(),prb.getPhysicsRotation());
					pd.setResting(true);
				}
			}else{
				pd.setResting(false);
			}
		}
	}

	private void threadPhysicsTickDebugLogDisplacementPerTick(PhysicsSpace ps,float tpf) {
		if(EDebug.LogDisplacementPerTick.b()){
			for(PhysicsRigidBody prb:ps.getRigidBodyList()){
				PhysicsData pd = getPhysicsDataFrom(prb);
				if(pd!=null){
					float fDist=prb.getPhysicsLocation().distance(pd.v3fPosAtPreviousTick);
					if(fDist>0){
						syso(pd.getInfo()+":stepDist="+fDist+","+"CcdMT="+prb.getCcdMotionThreshold()+",");
						pd.v3fPosAtPreviousTick=prb.getPhysicsLocation();//it is a copy
					}
				}
			}
		}
	}

	private void threadPhysicsTickCalcTPS(PhysicsSpace ps, float tpf) {
		// TPS ticks per second
		fThreadPhysTPF=tpf;
		iThreadPhysTickSum++;
		if(System.currentTimeMillis() > (lThreadPhysLastCalcTPS+1000)){
			lThreadPhysLastCalcTPS=System.currentTimeMillis();
			iThreadPhysTPS=iThreadPhysTickSum;
			iThreadPhysTickSum=0;
		}
	}

	public void resetForces(PhysicsData pd){
		pd.getPRB().setAngularVelocity(Vector3f.ZERO);
		pd.getPRB().setLinearVelocity(Vector3f.ZERO);
	}
	
	public void setEnabled(boolean enabled) {
		bullet.setEnabled(enabled);
	}

	public boolean isEnabled() {
		return bullet.isEnabled();
	}
	
	public boolean toggleEnabled(){
		setEnabled(!isEnabled());
		return isEnabled();
	}
	
	public void setBulletDebugVisualsEnabled(boolean debugEnabled) {
		bullet.setDebugEnabled(debugEnabled);
	}

	public boolean isBulletDebugVisualsEnabled() {
		return bullet.isDebugEnabled();
	}

	public float getSpeed() {
		return bullet.getSpeed();
	}

	public void setSpeed(float speed) {
		bullet.setSpeed(speed);
	}
	
//	public Impulse throwAtSelfDirImpulse(PhysicsData pd, float fDesiredSpeed){
//		return throwAtSelfDirImpulse(
//			pd.getSpatialWithPhysics(), 
//			(float) (fDesiredSpeed*pd.mts.getMassKg()) //the final speed depends on the mass
//		); 
//	}
	/**
	 * Do not use with bullets, they are too tiny, too little mass, too fast...
	 * For such bullets use raycast and apply forces on the hit target.
	 * @param spt
	 * @param fImpulseAtDirection
	 * @return 
	 */
	public ImpTorForce throwAtSelfDirImpulse(PhysicsData pd, float fDesiredSpeed){
		float fImpulseAtDirection=(float) (fDesiredSpeed*pd.mts.getMassKg()); //the final speed depends on the mass
//		PhysicsData pd = getPhysicsDataFrom(spt);
		if(pd!=null && pd.mts.getMassGrams()<50f && fImpulseAtDirection>300){ //9mm bullet weights 124 grains = 8 grams of lead
			MessagesI.i().warnMsg(this, "this looks like a bullet, avoid using this method!", pd, fImpulseAtDirection);
		}
		ImpTorForce imp = new ImpTorForce().setImpulseAtSelfDir(fImpulseAtDirection,null);
		PhysicsI.i().applyImpulseLater(pd, imp);
		pd.setLastImpuse(imp);
		return imp;
	}
	
	public Object debugTest(Object... aobj){
		return null; //keep even if emtpy
	}
	
	public void testDebugCreateMarker(ColorRGBA color, Vector3f v3f) {
		Geometry geom = GeometryI.i().create(MeshI.i().sphere(0.05f),color);
		geom.setLocalTranslation(v3f);
		AppI.i().getRootNode().attachChild(geom);
	}
	
	public ImpTorForce throwFromCam(PhysicsData pd,float fDesiredSpeed){
		// in front of cam pos
		AppI.i().placeAtCamWPos(pd, 1f, true); //orientated z
		ImpTorForce imp = throwAtSelfDirImpulse(pd,fDesiredSpeed);
		pdLastThrownFromCam=pd;
		return imp;
	}
	
	public void syncPhysTransfFromSpt(PhysicsData pd,boolean bLocation,boolean bRotation) {
		syncPhysTransfFromSpt(pd.getSpatialWithPhysics(), pd.getPRB(), bLocation, bRotation);
	}
	public void syncPhysTransfFromSpt(Spatial spt,PhysicsRigidBody prb, boolean bLocation, boolean bRotation) {
		if(bLocation)prb.setPhysicsLocation(spt.getWorldTranslation());
		if(bRotation)prb.setPhysicsRotation(spt.getWorldRotation());
	}
	public void syncPhysTransfFromSpt(Spatial spt) {
		RigidBodyControl rbc = spt.getControl(RigidBodyControl.class);
		syncPhysTransfFromSpt(spt, rbc, true, true);
	}
	
	/**
	 * generated collision events, not necessarily in the expected order :(
	 * it was necessary to make a raycast to determine the first collision :(
	 */
	@Override
	public void collision(PhysicsCollisionEvent event) {
		if(event.getNodeB()==null || event.getNodeA()==null)return;
		
		if(EDebug.LogCollisions.b()){
			syso(":collision():"+event.getNodeA().getName()+" <-> "+event.getNodeB().getName());
		}

		PhysicsData pdA = getPhysicsDataFrom(event.getNodeA());
		PhysicsData pdB = getPhysicsDataFrom(event.getNodeB());
		if(pdA==null || pdB==null) {
			return; //ignore non supported stuff
		}
		
		PhysicsProjectileI.i().glueProjectileCheckApply(pdA,pdB,event.getLocalPointB());
		PhysicsProjectileI.i().glueProjectileCheckApply(pdB,pdA,event.getLocalPointA());
		
		/**
		 * to let it continuously receive impact impulses, as it sleeps too fast
		 */
		if(pdA.isProjectile() && !pdB.isProjectile()) {
			pdB.forceAwakeSomeTicks(); //
		}
		if(pdB.isProjectile() && !pdA.isProjectile()) {
			pdA.forceAwakeSomeTicks();
		}
	}
	
	/**
	 * this is for collision groups {@link PhysicsCollisionGroupListener}
	 */
	@NotMainThread
	@Deprecated //in favor or naming clarity
	@Override
	public boolean collide(PhysicsCollisionObject nodeA,PhysicsCollisionObject nodeB) {
//		if(DetailedException.isExitRequested())return false; //may prevent further errors elsewhere by preventing all collisions?
		try{return threadPhysicsGroupCollideAllowedCheck(nodeA,nodeB);}catch(Exception ex){DetailedException.forceExitTrapIrrevocablySuspendCurrentThread(ex);}
		return false; //dummy, never reached...
	}
	/** 
	 * (new name also for clarity)
	 * 
	 * On a single tick it may be about to collide with more than one!!!
	 * And the collision order may NOT be the expected one!!! 
	 * @return if collision will be allowed and generate collision events at main thread TODO right?
	 */
	@NotMainThread
	protected boolean threadPhysicsGroupCollideAllowedCheck(PhysicsCollisionObject nodeA,PhysicsCollisionObject nodeB) {
//		if(CharacterI.i().isCharacter(nodeA))return true;
//		if(CharacterI.i().isCharacter(nodeB))return true;
		
		if(EDebug.TestDynamicPhysicsWithoutSpatialAndData.b()){
			if(nodeA.getUserObject()==null || nodeB.getUserObject()==null){
				return true;
			}
		}
		
		PhysicsData pdA = getPhysicsDataFrom((Spatial)nodeA.getUserObject());
		PhysicsData pdB = getPhysicsDataFrom((Spatial)nodeB.getUserObject());
		
		if(pdA==null || pdB==null){
			return false; //deny unsupported stuff TODO but... despite not generating collision events, it is colliding... :(
		}
		
		// spawned ignores its spawn source/parent
		if(pdA.containsPhysicsDataSkipCollisionGroup(pdB))return false;
		if(pdB.containsPhysicsDataSkipCollisionGroup(pdA))return false;
		
//		/** these two may not be working because the order of the collision may not be the expected, and the glue where would be set after other collisions on the same tick */
//		if(pdA.pdGlueWhere!=null)return pdA.pdGlueWhere==pdB; //TODO this seems useless
//		if(pdB.pdGlueWhere!=null)return pdB.pdGlueWhere==pdA; //TODO this seems useless
		
		if(pdA.isProjectile() && pdB.isProjectile()){
			return false;//prevent prjctle vs prjctle TODO allow if spawn source differs, allow also if already collided once with something else and is on a ricochet
		}
		
		if(bGlueAllowed){
//			if(true)return false;
			
			Boolean b=null;
			b=threadPhysicsGroupGlueDetectProjectileNextHit(pdA);
			if(b!=null){
				return b;
			}
			
			b=threadPhysicsGroupGlueDetectProjectileNextHit(pdB);
			if(b!=null){
				return b;
			}
			
			// skip if projectile didnt glue //TODO make it cause a scractch at least
			if(pdA.isProjectile() && !pdB.isProjectile()) {
				return false;
			}
			if(!pdA.isProjectile() && pdB.isProjectile()) {
				return false;
			}
		}
		
		return true; //allow all other collisions of PhysicsData
	}
	
	public static enum EDebug{
		AllowLog(true),
		
		/** use the temps to avoid having to restart the application, can even just rename them later! */
		Temp0,		Temp1,		Temp2,		Temp3,		Temp4,		Temp5,		Temp6,		Temp7,		Temp8, Temp9,
		
		LogDisintegrations, 
		
		LogDisplacementPerTick,
		LogCollisions, 
		TestDynamicPhysicsWithoutSpatialAndData, 
		LogDisintegrationByOutOfWorldBoundaries, 
		;
		EDebug(){}
		EDebug(boolean b){this.b=b;}
		boolean b;
		public void set(boolean b){this.b=b;}
		public boolean b() {
			return b;
		}
	}
	
	private Comparator<PhysicsRayTestResult> cmpRayNearest = new Comparator<PhysicsRayTestResult>() {
		@Override public int compare(PhysicsRayTestResult o1, PhysicsRayTestResult o2) {
			return Float.compare(o1.getHitFraction(),o2.getHitFraction());
		}
	};
	private float fPhysicsRayCastRange=300f;
	
//	@SuppressWarnings("unchecked")
//	public List<PhysicsRayTestResult> rayCastSortNearest(Vector3f v3fFrom, Vector3f v3fTo, boolean bIgnoreProjectiles) {
//		List<PhysicsRayTestResult> aprtrList = pspace.rayTest(v3fFrom,v3fTo);
//		if(bIgnoreProjectiles) {
//			
//		}
//		Collections.sort(aprtrList, cmpRayNearest);
//		return aprtrList;
//	}
	public static class RayCastResultX{
		private PhysicsRayTestResult resPhys;
		private CollisionResult resGeom;
		
		private PhysicsData pd;
		private Vector3f v3fWrldHit;

		private Vector3f v3fNormal;

		private float fDistance;
		private Geometry geom;
		
		private Vector3f v3fLocalHit;
		
		
		
//		private PhysicsRigidBody prb;
		
		public RayCastResultX(PhysicsRayTestResult resPhys,
			CollisionResult resGeom, PhysicsData pd, Geometry geom, Vector3f v3fWrldHit,
			Vector3f v3fNormal, float fDistance
		) {
			super();
			this.resPhys = resPhys;
			this.resGeom = resGeom;
			this.pd = pd;
			this.geom=geom;
			this.v3fWrldHit = v3fWrldHit;
			this.v3fNormal = v3fNormal;
			this.fDistance = fDistance;
			
			//TODO could the geometry not be aligned with the current physics position/rotation
			if(pd!=null)v3fLocalHit = pd.getGeomOriginalInitialLink().worldToLocal(getWHitPos(),null);
		}
		public CollisionResult getResGeom() {
			return resGeom;
		}
		public PhysicsRayTestResult getResPhys() {
			return resPhys;
		}
		public PhysicsData getPD() {
			return pd;
		}
		public Vector3f getWHitPos() {
			return v3fWrldHit;
		}
//		public PhysicsRigidBody getPrb() {
//			return prb;
//		}
		
		@Override
		public String toString() {
			return pd.toString();
		}
		public float getDistance() {
			return fDistance;
		}
		public Vector3f getNormal() {
			return v3fNormal;
		}
		public Geometry getGeom() {
			return geom;
		}
		public Vector3f getLocalHitPos() {
			return v3fLocalHit;//pd.getGeomOriginalInitialLink().worldToLocal(getWHitPos(),null);
		}
	}
	
	/**
	 * 
	 * @param v3fFrom
	 * @param v3fToOrDirection
	 * @param bIsDirection direction will be limited by the max range, if false you can decide how far it will be
	 * @param bIgnoreProjectiles
	 * @param bFirstOnly
	 * @param apdSkip
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<RayCastResultX> rayCastSortNearest(Vector3f v3fFrom, Vector3f v3fToOrDirection, boolean bIsDirection, boolean bIgnoreProjectiles, boolean bFirstOnly, PhysicsData... apdSkip) {
		ArrayList<RayCastResultX> apdrtrList = new ArrayList<RayCastResultX>();
		Vector3f v3fTo = bIsDirection ? 
			v3fFrom.add(v3fToOrDirection.normalize().mult(fPhysicsRayCastRange)) : 
			v3fToOrDirection;
		List<PhysicsRayTestResult> aprtrList = pspace.rayTest(v3fFrom,v3fTo);
		Collections.sort(aprtrList, cmpRayNearest);
		
		labelResults:for(PhysicsRayTestResult result:aprtrList){
			PhysicsData pdChk = getPhysicsDataFrom(result.getCollisionObject());
			if(pdChk!=null){ 
				if(bIgnoreProjectiles && pdChk.isProjectile())continue; //to skip/ignore projectile vs projectile
				for(PhysicsData pdSkip:apdSkip) {
					if(pdSkip==pdChk)continue labelResults;
				}
				
				assert pdChk.getPRB()==result.getCollisionObject();
				Vector3f v3fHit = v3fFrom.clone().interpolateLocal(v3fTo,result.getHitFraction());
				RayCastResultX resultx = new RayCastResultX(
					result, null, pdChk, pdChk.getGeomOriginalInitialLink(), v3fHit, result.getHitNormalLocal().clone(), 
					v3fFrom.distance(v3fHit) 
				);
				apdrtrList.add(resultx);
				if(bFirstOnly)break;
			}
		}
		
		return apdrtrList;
	}
	
	protected Boolean threadPhysicsGroupGlueDetectProjectileNextHit(PhysicsData pdProjectile){
		if(!pdProjectile.isProjectile())return null; //skip
		
		if(pdProjectile.pdGlueWhere!=null)return false;//1st hit only
		
		Vector3f v3fFrom = pdProjectile.prb.getPhysicsLocation();
		ArrayList<RayCastResultX> aresxList = rayCastSortNearest( // TODO use shortest than max range?
			v3fFrom,pdProjectile.prb.getLinearVelocity(),true,true,true,pdProjectile);
		if(aresxList.size()>0) {
			RayCastResultX resx = aresxList.get(0);
			boolean bGlued = pdProjectile.checkGluedAt(resx);
			
//			boolean bDeflected = pdProjectile.isHasGlueTargetDeflected();
			
			/**
			 * while hitting dynamic objects, the mass must remain > 0f to let forces be applied
			 */
			if(resx.pd.isTerrain() && bGlued){
				/**
				 * will be removed from physics space later
				 * "GLUE" ON TERRAIN HERE (not actually  glue, but terrain wont move anyway...)
				 * TODO changing mass here seems safe right?
				 */
				pdProjectile.prb.setMass(0f); //no need to be nested on a spatial when glueing on static terrain TODO instead check if nearest has mass=0? but it may be temporary and be glued would work better...
				pdProjectile.setPhysicsLocationAtMainThread(pdProjectile.v3fWorldGlueSpot);
//				pdProjectile.prb.setPhysicsLocation(pdProjectile.v3fWorldGlueSpot); //this positioning works precisely if done here, np, is easier, keep it here...
			}
			
			/**
			 * to collision groups work, Ccd must be disabled, but..
			 * to let impact impulses be applied, the projectile must not fail hitting the dynamic objects.
			 * the only way is to re-enable Ccd just before it is glued and removed from physics space!
			 * 
			 * TODO alternatively, apply impulse force and redirect projectile movement manually? this would allow ignoring Ccd and stil use collision groups! this would basically do bullet engine work manually...
			 * 
			 * TODO after the 1st glue attempt, Ccd will remain enabled auto hitting everything thru native bullet... find a workaround...
			 */
			pdProjectile.prb.setCcdMotionThreshold(pdProjectile.getCCdMotionThresholdBkp());
			
			return true; //to generate the collision event
		}
			
		return null; //to skip
	}
	
	public RayCastResultX applyImpulseHitTarget(Spatial sptRayCastFrom, Float fImpulse){
		Vector3f v3fDir = sptRayCastFrom.getWorldRotation().getRotationColumn(2).clone();
		Vector3f v3fPos = sptRayCastFrom.getWorldTranslation();
		return applyImpulseHitTarget(v3fPos,v3fDir,fImpulse);
	}
	public RayCastResultX applyImpulseHitTarget(PhysicsData pdRayCastFrom, Float fImpulse){
		Vector3f v3fDir = pdRayCastFrom.getPhysicsRotationCopy().getRotationColumn(2);
		Vector3f v3fPos = pdRayCastFrom.getPRB().getPhysicsLocation();
		return applyImpulseHitTarget(v3fPos,v3fDir,fImpulse);
	}
	public RayCastResultX applyImpulseHitTargetFromCam(Float fImpulse){
		Vector3f v3fDir = AppI.i().getCamLookingAtDir();
		Vector3f v3fPos = AppI.i().getCamWPos(0);
		return applyImpulseHitTarget(v3fPos,v3fDir,fImpulse);
	}
	/**
	 * ex.: use this for insta bullet shots or pushing things 
	 * @param fImpulse if null, the impulse will be based on the target's mass
	 * @return
	 */
	public RayCastResultX applyImpulseHitTarget(Vector3f v3fPos, Vector3f v3fDir, Float fImpulse){
		ArrayList<RayCastResultX> ares = rayCastSortNearest(v3fPos, v3fDir, true, false, true); 
		if(ares.size()>0){
			RayCastResultX res = ares.get(0);
			if(fImpulse==null)fImpulse=res.pd.getPRB().getMass();
			applyImpulseLater(res.pd,	new ImpTorForce().setImpulse(v3fDir,res.getLocalHitPos())	);
			return res;
		}
		
		return null;
	}
//	/**
//	 * ex.: use this for insta bullet shots or pushing things 
//	 * @param fImpulse if null, the impulse will be based on the target's mass
//	 * @return
//	 */
//	public RayCastResultX applyImpulseHitTargetAtCamDirection(Float fImpulse){
//		RayCastResultX res = getPhysicsDataAtCamDir(false, true);
//		if(res!=null){
//			if(fImpulse==null)fImpulse=res.pd.getPRB().getMass();
//			applyImpulseLater(res.pd,
//				new ImpTorForce()
//					.setImpulse(
//						AppI.i().getCamLookingAtDir().mult(fImpulse), 
//						res.pd.getGeomOriginalInitialLink().worldToLocal(res.getV3fWrldHit(),null)
//					)
//			);
//			
//			return res;
//		}
//		
//		return null;
//	}
	public RayCastResultX getPhysicsDataAtCamDir(boolean bIgnoreProjectiles, boolean bFirstOnly, PhysicsData... apdSkip){
//		for(CollisionResult cr:WorldPickingI.i().raycastPiercingAtCenter(null)){
//			PhysicsData pd = getPhysicsDataFrom(cr.getGeometry());
//			if(pd==null)continue;
//			return pd;
//		}
		ArrayList<RayCastResultX> a = rayCastSortNearest(
			AppI.i().getCamWPos(0f), 
//			AppI.i().getCamWPos(0f).add(AppI.i().getCamLookingAtDir().mult(getPhysicsRayCastRange())), 
			AppI.i().getCamLookingAtDir(), true,
			bIgnoreProjectiles, bFirstOnly, apdSkip);
		if(a.size()>0)return a.get(0);
		return null;
	}
	
	public void syso(String str){
		if(EDebug.AllowLog.b()){
			String strSep="/";
			System.out.println("[Phys]"
				+"Tk"+lTickCount+strSep
//				+"Tm"+System.nanoTime()+strSep
				+TimeFormatI.i().getRealTimeFormatted(null,"HH:mm:ss.SSS")+strSep
				+Thread.currentThread().getName()+strSep
				+str);
		}
	}
	
	public boolean isResting(PhysicsRigidBody prb){
		return (prb.getLinearVelocity().length()==0 && prb.getAngularVelocity().length()==0);
	}
	
	@Deprecated
	private void checkAndSaveSafeSpot(
		Spatial sptA, 
		Vector3f v3fA, 
		Quaternion quaRotA, 
		Spatial sptB, 
		Vector3f v3fB,  
		Quaternion quaRotB
	){
		PhysicsData pdA = getPhysicsDataFrom(sptA);
		PhysicsData pdB = getPhysicsDataFrom(sptB);
		
		if(pdA.isTerrain())pdB.saveSafePosRot(v3fB,quaRotB);
		if(pdB.isTerrain())pdA.saveSafePosRot(v3fA,quaRotA);
	}

	public void wakeUp(PhysicsData pd) {
		pd.prb.activate();
	}

	public void cancelDisintegration(PhysicsData pdWhat) {
		// prevent disintegration if glued on dynamic //TODO just increase the timeout or limit the amount per dynamic parent
		pdWhat.bAllowDisintegration=false;
//		hmDisintegratables.remove(pdWhat.rbc);
//		apdDisintegrate.remove(pdWhat);
	}

	public Vector3f getGravityCopy() {
		return pspace.getGravity(new Vector3f());
	}
	
	/**
	 * 
	 * @param color can be null
	 * @param fVolume
	 * @param str
	 * @param v3fPos
	 * @return
	 */
	public PhysicsData spawnVolumeBox(ColorRGBA color, float fVolume, String str, Vector3f v3fPos){
		if(color==null)color=ColorRGBA.Gray;
		
		Geometry geom = GeometryI.i().create(MeshI.i().box((float) (Math.cbrt(fVolume)/2f)), color);
		geom.setName("Box"+str);
		
//		/** to be on a node is important to let other things be attached to it like stuck projectiles */
//		Node node = new Node("TestBox"+str);
//		node.attachChild(geom);
		
		if(v3fPos!=null)geom.move(v3fPos); //b4 physics
		PhysicsData pd = PhysicsI.i().imbueFromWBounds(geom,new MatterStatus(EMatter.Generic1KgPerM3.get()),new Node());
		
		AppI.i().getRootNode().attachChild(pd.getSpatialWithPhysics());
		
		return pd;
	}
	
	public PhysicsData spawnWall(Vector3f v3fFrom, Vector3f v3fTo) {
		return spawnWall(v3fFrom, v3fTo, false, null,null,null);
	}
	/**
	 * this is easy to use with terrain editors
	 * @param v3fFrom
	 * @param v3fTo
	 * @param bFloorRamp true for ramps/floors, will be rotated in z 90deg. false for walls, will be moved upwards by half height (so its bottom is on the target spots)
	 * @return
	 */
	public PhysicsData spawnWall(Vector3f v3fFrom, Vector3f v3fTo, boolean bFloorRamp, Float fHeightOrWidth, Float fThickness, ColorRGBA color) {
		if(fThickness==null)fThickness=0.25f; //a common brick wall
		if(fHeightOrWidth==null)fHeightOrWidth=2f; // a simple common wall
		if(color==null)color=ColorRGBA.Gray;//concrete looks
		
		float fLength = v3fTo.distance(v3fFrom);
		Vector3f v3fDir = v3fTo.subtract(v3fFrom);
		
		Geometry geomWall=GeometryI.i().create(new Box(fThickness/2f, fHeightOrWidth/2f, fLength/2f), color);
		geomWall.setName("OrthoWall");
		AppI.i().getRootNode().attachChild(geomWall);
		
		geomWall.setLocalTranslation(v3fFrom);
		geomWall.lookAt(v3fTo, Vector3f.UNIT_Y);
		
		geomWall.move(geomWall.getLocalRotation().getRotationColumn(2).mult(fLength/2f));
		
		if(bFloorRamp) {
			geomWall.rotate(0, 0, 90*FastMath.DEG_TO_RAD);
		}else {
			//wall bottom
			geomWall.move(geomWall.getLocalRotation().getRotationColumn(1).mult(fHeightOrWidth/2f));
		}
		
		PhysicsData pd = PhysicsI.i().imbueFromWBounds(geomWall,new MatterStatus(EMatter.Generic1KgPerM3.get()),null).setTerrain(true);
		pd.getPRB().setMass(0f); //rbc
		
		return pd;
	}
	
//	/**
//	 * 
//	 * @param iPlane 0=x floor/ceiling, 1=y walls, 2=z easyRampsOriginOnBase 
//	 * @param fWidth
//	 * @param fHeight
//	 * @param fThickness can be null, will be default
//	 * @param v3fPos
//	 * @return
//	 */
//	@Deprecated //too messy..
//	public PhysicsData spawnWall(int iPlane,float fWidth,float fHeight,float fRadRotation,Float fThickness, Vector3f v3fPos){
//		if(fThickness==null)fThickness=0.1f;
//		
//		Geometry geomWall=GeometryI.i().create(new Box(fWidth/2f,fHeight/2f,fThickness/2f), ColorRGBA.Gray);
//		geomWall.setName("OrthoWall");
//		AppI.i().getRootNode().attachChild(geomWall);
////		Spatial sptAtRoot = geomWall;
//		
//		switch(iPlane){
//			case 0:
//				MiscJmeI.i().addToName(geomWall, "floor", false);
//				geomWall.rotate(90*FastMath.DEG_TO_RAD, 0, 0); //floors/ceilings
//				geomWall.rotate(0, fRadRotation, 0); //floors/ceilings
//				break;
//			case 1:
//				geomWall.rotate(0, fRadRotation, 0);
//				break; //default walls
//			case 2:
//				geomWall.rotate(0, 0, 90*FastMath.DEG_TO_RAD);
//				geomWall.setLocalTranslation(0,fWidth/2f,0);
//				
//				Node nodePivot = new Node("tmpPivotRot");
//				nodePivot.setLocalRotation(geomWall.getLocalRotation());
//				geomWall.getParent().attachChild(nodePivot);
//				RotateI.i().rotateAroundPivot(geomWall, nodePivot, fRadRotation, true);
//				nodePivot.removeFromParent();
//				
////				geomWall.rotate(0, 0, 90*FastMath.DEG_TO_RAD);
//				break;
//		}
//		
//		if(v3fPos!=null)geomWall.move(v3fPos); //b4 physics
//		PhysicsData pd = PhysicsI.i().imbueFromWBounds(geomWall).setTerrain(true);
//		pd.getPRB().setMass(0f); //rbc
//		
////		AppI.i().getRootNode().attachChild(sptAtRoot);
//		
//		return pd;
//	}

	public boolean isGlueAllowed() {
		return bGlueAllowed;
	}

	public PhysicsI setGlueAllowed(boolean bGlueAllowed) {
		this.bGlueAllowed = bGlueAllowed;
		return this; 
	}

	public boolean isDisableCcdToLetCollisionGroupsWork() {
		return bDisableCcdToLetCollisionGroupsWork;
	}

	public PhysicsI setDisableCcdToLetCollisionGroupsWork(boolean bDisableCcdToLetCollisionGroupsWork) {
		this.bDisableCcdToLetCollisionGroupsWork = bDisableCcdToLetCollisionGroupsWork;
		return this; 
	}

	public float getPhysicsRayCastRange() {
		return fPhysicsRayCastRange;
	}

	public PhysicsI setPhysicsRayCastRange(float fPhysicsRayCastRange) {
		this.fPhysicsRayCastRange = fPhysicsRayCastRange;
		return this; 
	}

	public float getDefaultDeflectionAngle() {
		return fDeflectionAngle;
	}

	public PhysicsI setDeflectionAngle(float fDeflectionAngle) {
		this.fDeflectionAngle = fDeflectionAngle;
		return this; 
	}


}
