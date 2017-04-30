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

import com.github.devconslejme.misc.GlobalManagerI;
import com.jme3.app.Application;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.font.BitmapText;
import com.jme3.font.LineWrapMode;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
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
	
	public Vector3f getBoundingBoxSize(Spatial spt){
		BoundingVolume bv = spt.getWorldBound();
		if(bv==null)return null; //it is not ready yet
		
		if(bv instanceof BoundingBox){
			return ((BoundingBox)bv).getExtent(null).mult(2f);
		}
		return null;
	}
	
	public void recursivelyApplyTextNoWrap(Node nodeParent) {
		for(Spatial spt:nodeParent.getChildren()){
			if(spt instanceof BitmapText){
				((BitmapText)spt).setLineWrapMode(LineWrapMode.NoWrap);
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
		Vector2f v2f = GlobalManagerI.i().get(Application.class).getInputManager().getCursorPosition();
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
		return new Vector3f(v2f.x,v2f.y,0);
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
}
