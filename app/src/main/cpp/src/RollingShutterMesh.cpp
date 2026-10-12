#include "RollingShutterMesh.hpp"
#include <cmath>

RollingShutterMesh::RollingShutterMesh(int gridCols, int gridRows)
    : cols(gridCols), rows(gridRows) {
    generateStaticGrid();
}

void RollingShutterMesh::generateStaticGrid() {
    vertices.clear();
    indices.clear();

    vertices.reserve((cols + 1) * (rows + 1));
    for (int r = 0; r <= rows; ++r) {
        float normY = static_cast<float>(r) / static_cast<float>(rows);
        float ndcY = 1.0f - 2.0f * normY; // Dari 1.0 (atas) ke -1.0 (bawah)

        for (int c = 0; c <= cols; ++c) {
            float normX = static_cast<float>(c) / static_cast<float>(cols);
            float ndcX = 2.0f * normX - 1.0f; // Dari -1.0 (kiri) ke 1.0 (kanan)

            Vertex2D v;
            v.x = ndcX;
            v.y = ndcY;
            v.u = normX;
            v.v = normY;
            vertices.push_back(v);
        }
    }

    // Bangun Triangle Strip / Triangles indices
    indices.reserve(cols * rows * 6);
    for (int r = 0; r < rows; ++r) {
        for (int c = 0; c < cols; ++c) {
            int topLeft = r * (cols + 1) + c;
            int topRight = topLeft + 1;
            int bottomLeft = (r + 1) * (cols + 1) + c;
            int bottomRight = bottomLeft + 1;

            // Segitiga 1
            indices.push_back(topLeft);
            indices.push_back(bottomLeft);
            indices.push_back(topRight);

            // Segitiga 2
            indices.push_back(topRight);
            indices.push_back(bottomLeft);
            indices.push_back(bottomRight);
        }
    }
}

void RollingShutterMesh::updateMesh(float pitchDelta, float yawDelta, float rollDelta, float readoutTimeRatio) {
    // Margin crop 15% untuk menyerap distorsi tepi
    const float cropScale = 0.85f;
    const float halfCropOffset = (1.0f - cropScale) * 0.5f;

    for (int r = 0; r <= rows; ++r) {
        float scanlineFraction = static_cast<float>(r) / static_cast<float>(rows);
        // Deformasi diferensial per baris scanline sensor (kunci anti-jelly)
        float rowPhaseOffset = (scanlineFraction - 0.5f) * readoutTimeRatio;

        float effectiveYaw = yawDelta + (yawDelta * rowPhaseOffset);
        float effectivePitch = pitchDelta + (pitchDelta * rowPhaseOffset);
        float effectiveRoll = rollDelta + (rollDelta * rowPhaseOffset);

        float cosR = std::cos(-effectiveRoll);
        float sinR = std::sin(-effectiveRoll);

        for (int c = 0; c <= cols; ++c) {
            float baseU = static_cast<float>(c) / static_cast<float>(cols);
            float baseV = scanlineFraction;

            // Skala ke dalam crop area
            float centeredU = (baseU - 0.5f) * cropScale;
            float centeredV = (baseV - 0.5f) * cropScale;

            // Rotasi roll horizon
            float rotU = centeredU * cosR - centeredV * sinR;
            float rotV = centeredU * sinR + centeredV * cosR;

            // Translasi yaw dan pitch
            rotU -= effectiveYaw * 0.45f;
            rotV += effectivePitch * 0.45f;

            // Kembalikan ke koordinat [0.0, 1.0]
            int vertexIndex = r * (cols + 1) + c;
            vertices[vertexIndex].u = rotU + 0.5f;
            vertices[vertexIndex].v = rotV + 0.5f;
        }
    }
}
