#ifndef KINETIC_ROLLING_SHUTTER_MESH_HPP
#define KINETIC_ROLLING_SHUTTER_MESH_HPP

#include <vector>

struct Vertex2D {
    float x, y;     // Posisi layar [-1.0, 1.0]
    float u, v;     // Koordinat tekstur kamera [0.0, 1.0]
};

class RollingShutterMesh {
public:
    RollingShutterMesh(int gridCols = 32, int gridRows = 32);
    ~RollingShutterMesh() = default;

    void updateMesh(float pitchDelta, float yawDelta, float rollDelta, float readoutTimeRatio = 0.02f);

    const std::vector<Vertex2D>& getVertices() const { return vertices; }
    const std::vector<unsigned short>& getIndices() const { return indices; }
    int getIndexCount() const { return static_cast<int>(indices.size()); }

private:
    int cols;
    int rows;
    std::vector<Vertex2D> vertices;
    std::vector<unsigned short> indices;

    void generateStaticGrid();
};

#endif // KINETIC_ROLLING_SHUTTER_MESH_HPP
