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

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import com.github.devconslejme.misc.Annotations.ToDo;
import com.github.devconslejme.misc.AssertionsI;
import com.github.devconslejme.misc.DetailedException;
import com.github.devconslejme.misc.GlobalManagerI;
import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.font.LineWrapMode;
import com.jme3.math.FastMath;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Mesh.Mode;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

/**
 * @DevSelfNote Misc lib class should not exist. As soon coehsion is possible, do it!
 * @author Henrique Abdalla <https://github.com/AquariusPower><https://sourceforge.net/u/teike/profile/>
 */
public class MiscJmeI {
	public static MiscJmeI i(){return GlobalManagerI.i().get(MiscJmeI.class);}

	private Float	fAboveAllAtGuiNode=null;
	private Application	app;
	private Node	nodeVirtualWorld;
	private SimpleApplication	sappOptional;
	
	/**
	 * 
	 * @param nodeVirtualWorld if null, will try to auto-set from simple application
	 */
	public void configure(Node nodeVirtualWorld){
		this.app=GlobalManagerI.i().get(Application.class);
		if (this.app instanceof SimpleApplication)sappOptional = (SimpleApplication) this.app;
		
		setNodeVirtualWorld(nodeVirtualWorld);
		if(isSimpleApplication() && getNodeVirtualWorld()==null){
			setNodeVirtualWorld(sappOptional.getRootNode());
		}
		
		AssertionsI.i().putAssertRemainUnmodified(Vector3f.UNIT_XYZ, Vector3f.UNIT_XYZ.clone());
		AssertionsI.i().putAssertRemainUnmodified(Vector3f.UNIT_X, Vector3f.UNIT_X.clone());
		AssertionsI.i().putAssertRemainUnmodified(Vector3f.UNIT_Y, Vector3f.UNIT_Y.clone());
		AssertionsI.i().putAssertRemainUnmodified(Vector3f.UNIT_Z, Vector3f.UNIT_Z.clone());
		AssertionsI.i().putAssertRemainUnmodified(Vector3f.NAN, Vector3f.NAN.clone());
	}
	
	public boolean isInside(Spatial sptWorldBoundLimits, Vector3f v3fChkPos){
		return isInside(sptWorldBoundLimits, v3fChkPos, false);
	}
	public boolean isInside(Spatial sptWorldBoundLimits, Vector3f v3fChkPos, boolean bIgnoreZ){
		BoundingVolume bv = sptWorldBoundLimits.getWorldBound();
		if(bv==null)return false; //it is not ready yet
		
		if(bIgnoreZ){
			v3fChkPos=v3fChkPos.clone();
			v3fChkPos.z=bv.getCenter().z;
		}
		
		return bv.contains(v3fChkPos);
//		
//		Vector3f v3f = sptWorldBoundLimits.getWorldTranslation();
//		if(v3fChkPos.x<v3f.x)return false;
//		if(v3fChkPos.y<v3f.y)return false;
//		if(v3fChkPos.z<v3f.z)return false;
//		
//		Vector3f v3fSize = getBoundingBoxSize(sptWorldBoundLimits);
//		if(v3fChkPos.x>(v3f.x+v3fSize.x))return false;
//		if(v3fChkPos.y>(v3f.y+v3fSize.y))return false;
//		if(v3fChkPos.z>(v3f.z+v3fSize.z))return false;
//		
//		return true;
	}
	
	public Vector3f getBoundingBoxSizeCopy(Spatial spt){
		BoundingVolume bv = spt.getWorldBound();
		if(bv==null)return null; //it is not ready yet
		
		if(bv instanceof BoundingBox){
			return ((BoundingBox)bv).getExtent(null).mult(2f);
		}
		return null;
	}
	
	public void recursivelyApplyTextNoWrap(Node nodeParent) {
		/**
		 * LineWrapMode.Clip look better than NoWrap
		 */
		LineWrapMode e = LineWrapMode.Clip; //TODO could it be a clip only in the height? so it would wrap but would be clipped in the height only if overflowing downwards or outside limits 
		for(Spatial spt:nodeParent.getChildren()){
			if(spt instanceof BitmapText){
//				System.err.println("NoWrapAt:"+((BitmapText)spt).getText());//TODO rm
				if(!((BitmapText)spt).getLineWrapMode().equals(e)){
					((BitmapText)spt).setLineWrapMode(e);
				}
			}
			if(spt instanceof Node){
				recursivelyApplyTextNoWrap((Node)spt);
			}
		}
	}

//	public BitmapText getBitmapTextFrom(Node node){
//		for(Spatial c : node.getChildren()){
//			if(c instanceof BitmapText){
//				return (BitmapText)c;
//			}
//		}
//		return null;
//	}
	
	
	public Vector3f getMouseCursorPosition(){
		Vector2f v2f = app.getInputManager().getCursorPosition();
		return new Vector3f(v2f.x,v2f.y,0);
	}

	/**
	 * 
	 * @param av3f each dot from the multi-line
	 * @return
	 */
	public Mesh updateMultiLineMesh(Mesh mesh, Vector3f[] av3f){
		if(mesh==null)mesh=new Mesh();
//		mesh.setStreamed();
		mesh.setMode(Mode.LineStrip);
		
		FloatBuffer fbuf = BufferUtils.createFloatBuffer(av3f);
		mesh.setBuffer(Type.Position,3,fbuf);
		
		ShortBuffer sbuf = BufferUtils.createShortBuffer(av3f.length);
		for(Short si=0;si<sbuf.capacity();si++){sbuf.put(si);}
		
		mesh.setBuffer(Type.Index,1,sbuf);
		
		mesh.updateBound();
		mesh.updateCounts();
		
		return mesh;
	}
	
	public void addToName(Spatial spt, String str, boolean bPrepend){
		if(bPrepend){
			spt.setName(str+"/"+spt.getName());
		}else{
			spt.setName(spt.getName()+"/"+str);
		}
	}
	
	public Vector2f toV2f(Vector3f v3f) {
		return new Vector2f(v3f.x,v3f.y);
	}
		
	public Vector3f toV3f(Vector2f v2f) {
		return toV3f(v2f, 0);
	}
	public Vector3f toV3f(Vector2f v2f,float fZ) {
		return new Vector3f(v2f.x,v2f.y,fZ);
	}

	public Vector3f getWorldCenterPosCopy(Spatial sptTarget) {
		return sptTarget.getWorldBound().getCenter().clone();
//		return sptTarget.getLocalTranslation().add(getBoundingBoxSize(sptTarget).mult(0.5f));
	}
	
	public Vector3f randomDirection(){
		return new Vector3f(
			FastMath.nextRandomFloat()*2f-1f,
			FastMath.nextRandomFloat()*2f-1f,
			FastMath.nextRandomFloat()*2f-1f).normalize();
	}
	
  public BitmapFont loadDefaultFont() {
  	return loadFont("Interface/Fonts/Default.fnt");
  }
  public BitmapFont loadDefaultMonoFont() {
  	return loadFont("Interface/Fonts/Console.fnt");
  }
  public BitmapFont loadFont(String strPath) {
  	return GlobalManagerI.i().get(Application.class).getAssetManager().loadFont(strPath);
  }
  
  public void setAboveAllAtGuiNode(float f){
		if(this.fAboveAllAtGuiNode!=null)throw new DetailedException("already set");
  	this.fAboveAllAtGuiNode=f;
  }
  
  /**
   * TODO use the Gui Node bounding box Z? but then, it would be required to be updated whenever it changes... better keep using a default above all like from lemur 1001 (raycast from 1000 down)
   * @return
   */
  public float getZAboveAllAtGuiNode(){
  	return fAboveAllAtGuiNode; 
  }

  
  /**
   * This mode prefer hitting the corners.
   * TODO Too much work to complete this, another day? dunno..
   * @param nodeBound
   * @param v3f
   * @return
   */
  @ToDo
  @Deprecated //just to avoid using it, it is actually incomplete...
  public Vector3f getNearestCornerSpotInside(Node nodeBound, Vector3f v3f) {
		BoundingBox bb = (BoundingBox)nodeBound.getWorldBound();
  	if(bb.intersects(v3f))return v3f;
  	
    boolean bX = FastMath.abs(bb.getCenter().x - v3f.x) <= bb.getXExtent();
    boolean bY = FastMath.abs(bb.getCenter().y - v3f.y) <= bb.getYExtent();
    boolean bZ = FastMath.abs(bb.getCenter().z - v3f.z) <= bb.getZExtent();
    
    if(bY&&bZ&&!bX)v3f.x = bb.getCenter().x + (v3f.x>bb.getCenter().x?1:-1)*bb.getXExtent();
    //TODO
    
//  	if(
//  			v3f.x < bb.getCenter().x - bb.getXExtent()
//  	){
//  	}
  	
    throw new UnsupportedOperationException("incomplete method");
//		return v3f;
  }
  
	public Vector3f getNearestSpotInside(Node nodeBound, Vector3f v3fRayCastOrigin) {
		BoundingBox bb = (BoundingBox)nodeBound.getWorldBound();
		Ray ray = new Ray(v3fRayCastOrigin, bb.getCenter().subtract(v3fRayCastOrigin).normalize());
		CollisionResults results = new CollisionResults();
		if(bb.collideWith(ray, results)>0){ //wont work if use the collideWith() of the node!!!
			return results.getClosestCollision().getContactPoint();
		}
		
		return v3fRayCastOrigin;
	}
	

	public Node getNodeVirtualWorld() {
		return nodeVirtualWorld;
	}

	public MiscJmeI setNodeVirtualWorld(Node nodeVirtualWorld) {
		this.nodeVirtualWorld = nodeVirtualWorld;
		return this;
	}

	public Application getApp() {
		return app;
	}
	
	public boolean isSimpleApplication(){
		return sappOptional!=null;
	}
	
	public SimpleApplication getSApp(){
		return sappOptional;
	}
}
