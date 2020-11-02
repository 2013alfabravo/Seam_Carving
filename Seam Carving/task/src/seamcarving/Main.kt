package seamcarving

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.Exception
import kotlin.math.min
import kotlin.math.sqrt

fun main(args: Array<String>) {
    val inputFileName = parseArgs("-in", args) ?: return
    val outputFileName = parseArgs("-out", args) ?: return
    val verticalSeamsToRemove = parseArgs("-width", args)?.toInt() ?: 0
    val horizontalSeamsToRemove = parseArgs("-height", args)?.toInt() ?: 0

    var srcImage = readImageFromDisk(inputFileName) ?: run {
        println("Failed to read file: $inputFileName")
        return
    }

    repeat(verticalSeamsToRemove) {
        srcImage = resize(srcImage)
    }

    srcImage = srcImage.transpose()

    repeat(horizontalSeamsToRemove) {
        srcImage = resize(srcImage)
    }

    srcImage = srcImage.transpose()

    val success = saveImageOnDisk(srcImage, outputFileName)
    if (!success) {
        println("Failed to write file: $outputFileName")
    }
}

private fun resize(img: BufferedImage): BufferedImage {
    val energyMatrix = createEnergyMatrix(img)
    calcPixelWeights(energyMatrix)
    val optimalSeam = getVerticalSeam(energyMatrix)
    return img.deleteSeam(optimalSeam)
}

private fun parseArgs(arg: String, args: Array<String>): String? {
    for (i in args.indices step 2) {
        if (args[i] == arg) return args[i + 1]
    }
    return null
}

private fun getVerticalSeam(matrix: Array<DoubleArray>): IntArray {
    val minBottomRowEnergy = matrix[matrix.size - 1].minOrNull() ?: 0.0
    val bottomStartPixel = matrix[matrix.size - 1].indexOfFirst { it == minBottomRowEnergy }
    val seam = IntArray(matrix.size) { 0 }

    seam[matrix.size - 1] = bottomStartPixel
    for (row in matrix.size - 2 downTo 0) {
        val x = when (val prevX = seam[row + 1]) {
            0 -> if (matrix[row][prevX + 1] < matrix[row][prevX]) prevX + 1 else prevX
            matrix[0].size - 1 -> if (matrix[row][prevX] < matrix[row][prevX - 1]) prevX else prevX - 1
            else -> {
                if (matrix[row][prevX + 1] < matrix[row][prevX] && matrix[row][prevX + 1] < matrix[row][prevX - 1]) {
                    prevX + 1
                } else if (matrix[row][prevX] < matrix[row][prevX - 1] && matrix[row][prevX] <= matrix[row][prevX + 1]) {
                    prevX
                } else {
                    prevX - 1
                }
            }
        }
        seam[row] = x
    }
    return seam
}

private fun createEnergyMatrix(img: BufferedImage): Array<DoubleArray> {
    val matrix = Array(img.height) { DoubleArray(img.width) { 0.0 } }
    for (x in 0 until img.width) {
        for (y in 0 until img.height) {
            matrix[y][x] = calcPixelEnergy(x, y, img)
        }
    }
    return matrix
}

private fun readImageFromDisk(inputFileName: String): BufferedImage? {
    return try {
        ImageIO.read(File(inputFileName))
    } catch (e: Exception) {
        null
    }
}

private fun saveImageOnDisk(srcImage: BufferedImage, outputFileName: String): Boolean {
    return try {
        ImageIO.write(srcImage, "png", File(outputFileName))
        true
    } catch (e: Exception) {
        false
    }
}

private fun calcPixelEnergy(x: Int, y: Int, img: BufferedImage): Double {
    val xPlus = if (x == 0) x + 2 else if (x == img.width - 1) x else x + 1
    val xMinus = if (x == 0) x else if (x == img.width - 1) x - 2 else x - 1

    val dxSquared = (Color(img.getRGB(xPlus, y)).red - Color(img.getRGB(xMinus, y)).red).square() +
            (Color(img.getRGB(xPlus, y)).green - Color(img.getRGB(xMinus, y)).green).square() +
            (Color(img.getRGB(xPlus, y)).blue - Color(img.getRGB(xMinus, y)).blue).square()

    val yPlus = if (y == 0) y + 2 else if (y == img.height - 1)  y else y + 1
    val yMinus = if (y == 0) y else if (y == img.height - 1) y - 2 else y - 1

    val dySquared = (Color(img.getRGB(x, yPlus)).red - Color(img.getRGB(x, yMinus)).red).square() +
            (Color(img.getRGB(x, yPlus)).green - Color(img.getRGB(x, yMinus)).green).square() +
            (Color(img.getRGB(x, yPlus)).blue - Color(img.getRGB(x, yMinus)).blue).square()

    return sqrt(dxSquared.toDouble() + dySquared.toDouble())
}

private fun calcPixelWeights(matrix: Array<DoubleArray>) {
    for (y in 1 until matrix.size) {
        for (x in matrix[y].indices) {
            when (x) {
                0 -> matrix[y][x] += min(matrix[y - 1][x], matrix[y - 1][x + 1])
                matrix[y].size - 1 -> matrix[y][x] += min(matrix[y - 1][x - 1], matrix[y - 1][x])
                else -> matrix[y][x] += min(matrix[y - 1][x + 1], min(matrix[y - 1][x - 1], matrix[y - 1][x]))
            }
        }
    }
}

private fun Int.square() = this * this

private fun BufferedImage.transpose(): BufferedImage {
    val transposed = BufferedImage(this.height, this.width, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until this.height) {
        for (x in 0 until this.width) {
            transposed.setRGB(y, x, this.getRGB(x, y))
        }
    }
    return transposed
}

private fun BufferedImage.deleteSeam(seam: IntArray): BufferedImage {
    val resized = BufferedImage(this.width - 1, this.height, BufferedImage.TYPE_INT_RGB)
    var targetX = 0
    for (y in 0 until this.height) {
        for (x in 0 until this.width) {
            if (x != seam[y]) {
                resized.setRGB(targetX, y, this.getRGB(x, y))
                targetX++
            }
        }
        targetX = 0
    }
    return resized
}