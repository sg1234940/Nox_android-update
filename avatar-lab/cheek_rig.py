"""Add soft cheek tissue to the jaw shape of the reviewed Iskra Lab mesh.

The neutral face, mouth seam, textures, skin weights and animation clips keep
their original bytes. Units and coordinates below are in the mesh's local metres.
"""
import hashlib
import json
import math
import struct

SOURCE_SHA = '451ebc87620b95500163716e77b7433d6ef7d765a2b33391a0df08f2b921848c'
VEC = struct.Struct('<3f')
# Centres follow the slight turn and asymmetry of the scanned face.
CENTRES = ((-.050, 1.696, .117), (.039, 1.697, .114))


def add_cheeks(source):
    if hashlib.sha256(source).hexdigest() != SOURCE_SHA:
        raise ValueError('Cheek landmarks are defined only for the reviewed source GLB')
    size, = struct.unpack_from('<I', source, 12)
    gltf = json.loads(source[20:20 + size])
    data = bytearray(source)
    mesh = gltf['meshes'][0]
    face = mesh['primitives'][0]
    jaw = face['targets'][mesh['extras']['targetNames'].index('jawOpen')]

    def offset(index):
        accessor = gltf['accessors'][index]
        view = gltf['bufferViews'][accessor['bufferView']]
        assert accessor['type'] == 'VEC3' and accessor['componentType'] == 5126
        assert view.get('byteStride', VEC.size) == VEC.size
        return 28 + size + view.get('byteOffset', 0) + accessor.get('byteOffset', 0)

    positions = offset(face['attributes']['POSITION'])
    normals = offset(face['attributes']['NORMAL'])
    jaw_moves = offset(jaw['POSITION'])
    jaw_normals = offset(jaw['NORMAL'])
    count = gltf['accessors'][face['attributes']['POSITION']]['count']
    for i in range(count):
        index = i * VEC.size
        point = VEC.unpack_from(source, positions + index)
        side = int(point[0] >= 0)
        # Smooth compact support with zero slope at the boundary. Both cheeks
        # follow the opening jaw down, slightly inward and back toward the hinge.
        # The short lower falloff ends above the mouth seam.
        radii = (.030, .017 if point[1] < CENTRES[side][1] else .033, .026)
        unit = [(p - centre) / radius for p, centre, radius in zip(point, CENTRES[side], radii)]
        t = 1 - sum(x*x for x in unit)
        if t <= 0:
            continue
        direction = ((-.0018 if side else .0018), -.008, -.0025)
        shift = VEC.unpack_from(source, jaw_moves + index)
        VEC.pack_into(data, jaw_moves + index, *(s + d*t*t for s, d in zip(shift, direction)))

        # Inverse-transpose of the local deformation gradient. Keep the previous
        # jaw shape's shading and update only the normal change due to the cheek.
        normal = VEC.unpack_from(source, normals + index)
        delta = VEC.unpack_from(source, jaw_normals + index)
        old = [n + d for n, d in zip(normal, delta)]
        grad = [-4*t*x/r for x, r in zip(unit, radii)]
        factor = sum(d*n for d, n in zip(direction, old)) / (1 + sum(d*g for d, g in zip(direction, grad)))
        moved = [n - g*factor for n, g in zip(old, grad)]
        length = math.sqrt(sum(n*n for n in moved))
        VEC.pack_into(data, jaw_normals + index, *(n/length - base for n, base in zip(moved, normal)))
    return data
