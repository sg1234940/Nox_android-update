"""Extend the Lab 0.6.9 scan with independent facial shapes and its original PBR atlas."""
import hashlib
import json
import math
import struct

SOURCE = '0f1d4325bcda5504acee3cfe57b35797eb8c15f1cf5fed1cb0dd70286577b4cc'
VEC = struct.Struct('<3f')
# Local metres, anatomical left is on the viewer's right.
SHAPES = (
    ('cheekRaiseLeft', (.039, 1.697, .114), (.030, .026, .024), (.0015, .0054, .0020)),
    ('cheekRaiseRight', (-.050, 1.696, .117), (.030, .026, .024), (-.0015, .0054, .0020)),
    ('foreheadLiftLeft', (.025, 1.791, .130), (.030, .024, .030), (.0005, .0040, .0008)),
    ('foreheadLiftRight', (-.038, 1.791, .145), (.030, .024, .030), (-.0005, .0040, .0008)),
)


def extend(source):
    if hashlib.sha256(source).hexdigest() != SOURCE:
        raise ValueError('The facial landmarks require the Lab 0.6.9 scan')
    size, = struct.unpack_from('<I', source, 12)
    doc = json.loads(source[20:20 + size])
    binary = bytearray(source[28 + size:][:doc['buffers'][0]['byteLength']])
    mesh = doc['meshes'][0]
    prim = mesh['primitives'][0]
    accessors, views = doc['accessors'], doc['bufferViews']

    def vectors(index):
        a = accessors[index]
        v = views[a['bufferView']]
        assert a['componentType'] == 5126 and a['type'] == 'VEC3' and v.get('byteStride', 12) == 12
        offset = v.get('byteOffset', 0) + a.get('byteOffset', 0)
        return list(VEC.iter_unpack(binary[offset:offset+12*a['count']]))

    def append(values):
        while len(binary) % 4:
            binary.append(0)
        data = b''.join(VEC.pack(*v) for v in values)
        views.append(dict(buffer=0, byteOffset=len(binary), byteLength=len(data), target=34962))
        binary.extend(data)
        accessors.append(dict(bufferView=len(views)-1, componentType=5126, count=len(values), type='VEC3',
                              min=list(map(min,zip(*values))), max=list(map(max,zip(*values)))))
        return len(accessors)-1

    points = vectors(prim['attributes']['POSITION'])
    normals = vectors(prim['attributes']['NORMAL'])
    for name, centre, radii, delta in SHAPES:
        displacements, normal_deltas = [], []
        for p, n in zip(points, normals):
            u = [(a-b)/r for a,b,r in zip(p,centre,radii)]
            t = max(0., 1.-sum(x*x for x in u))
            displacements.append(tuple(d*t*t for d in delta))
            if not t:
                normal_deltas.append((0.,0.,0.))
                continue
            grad = [-4*t*x/r for x,r in zip(u,radii)]
            factor = sum(d*k for d,k in zip(delta,n))/(1+sum(d*g for d,g in zip(delta,grad)))
            moved = [k-g*factor for k,g in zip(n,grad)]
            length = math.sqrt(sum(k*k for k in moved))
            normal_deltas.append(tuple(k/length-old for k,old in zip(moved,n)))
        assert sum(any(d) for d in displacements) >= 15, name
        target = dict(POSITION=append(displacements), NORMAL=append(normal_deltas))
        for primitive in mesh['primitives']:
            primitive['targets'].append(target)
        mesh['weights'].append(0)
        mesh['extras']['targetNames'].append(name)
    # The red channel of the packed atlas is constant. Green stores roughness, blue stores metalness.
    surface = doc['materials'][prim['material']]
    surface['normalTexture']['scale'] = .38
    surface['pbrMetallicRoughness'].update(metallicRoughnessTexture={'index':2}, roughnessFactor=1.0, metallicFactor=.65)
    doc['buffers'][0]['byteLength'] = len(binary)
    binary.extend(bytes(-len(binary)%4))
    meta = json.dumps(doc,separators=(',',':'),ensure_ascii=False).encode()
    meta += b' '*(-len(meta)%4)
    return struct.pack('<5I',0x46546c67,2,28+len(meta)+len(binary),len(meta),0x4e4f534a)+meta+struct.pack('<2I',len(binary),0x004e4942)+binary
