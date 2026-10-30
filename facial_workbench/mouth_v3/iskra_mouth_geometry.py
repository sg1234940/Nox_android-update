"""Narrow-phase triangle collision; independent of Blender for regression tests."""
from iskra_mouth_cut_plan import sub,add,mul,dot,cross,norm,orient,on_segment

def distance(a,b):return norm(sub(a,b))

def segment_triangle(a,b,tri,eps):
    p,q,r=tri;e1,e2=sub(q,p),sub(r,p);d=sub(b,a);h=cross(d,e2);det=dot(e1,h)
    if abs(det)<=eps*max(norm(e1)*norm(e2),eps):return None
    inv=1/det;s=sub(a,p);u=inv*dot(s,h);v=inv*dot(d,cross(s,e1));t=inv*dot(e2,cross(s,e1))
    tol=eps/max(norm(e1),norm(e2),eps)
    if -tol<=u<=1+tol and -tol<=v and u+v<=1+tol and -tol<=t<=1+tol:return add(a,mul(d,t))
    return None

def clipped_area(a,b,eps):
    sign=1 if orient(*b)>0 else -1;poly=list(a)
    for c,d in zip(b,b[1:]+b[:1]):
        out=[]
        for p,q in zip(poly,poly[1:]+poly[:1]):
            sp,sq=sign*orient(c,d,p),sign*orient(c,d,q)
            pin,qin=sp>=-eps,sq>=-eps
            if pin:out.append(p)
            if pin!=qin:
                t=sp/(sp-sq);out.append(tuple(p[k]+t*(q[k]-p[k]) for k in range(2)))
        poly=out
        if not poly:return 0
    return abs(sum(p[0]*q[1]-q[0]*p[1] for p,q in zip(poly,poly[1:]+poly[:1])))/2

def illegal_intersection(coords,a,b,eps):
    pa,pb=[coords[i] for i in a],[coords[i] for i in b];shared=set(a)&set(b)
    normal=cross(sub(pa[1],pa[0]),sub(pa[2],pa[0]));nn=norm(normal)
    if nn<=eps*eps:return True
    coplanar=all(abs(dot(sub(p,pa[0]),normal))<=eps*nn for p in pb)
    if coplanar:
        k=max(range(3),key=lambda i:abs(normal[i]));project=lambda p:tuple(p[j] for j in range(3) if j!=k)
        a2,b2=list(map(project,pa)),list(map(project,pb))
        if clipped_area(a2,b2,eps*eps)>eps*eps:return True
        # Zero-area contact is permitted only on actual shared source IDs.
        # Check endpoints touching another triangle's edges, not merely distance.
        for ids,pp,other in ((a,a2,b2),(b,b2,a2)):
            for i,p in zip(ids,pp):
                if i not in shared and any(on_segment(x,y,p,eps*eps) for x,y in zip(other,other[1:]+other[:1])):return True
        return False
    for ids,pp,other in ((a,pa,pb),(b,pb,pa)):
        for p,q in zip(pp,pp[1:]+pp[:1]):
            hit=segment_triangle(p,q,other,eps)
            if hit is not None:
                if any(distance(hit,coords[i])<=eps for i in shared):continue
                if len(shared)==2:
                    u,v=[coords[i] for i in shared];d=sub(v,u);t=dot(sub(hit,u),d)/dot(d,d)
                    if -eps<=t<=1+eps and distance(hit,add(u,mul(d,t)))<=eps:continue
                return True
    return False
