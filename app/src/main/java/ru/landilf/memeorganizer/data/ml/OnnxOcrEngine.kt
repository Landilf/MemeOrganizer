package ru.landilf.memeorganizer.data.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.RotatedRect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import ru.landilf.memeorganizer.domain.model.ml.OcrEngine
import ru.landilf.memeorganizer.domain.model.ml.OcrImage
import ru.landilf.memeorganizer.domain.model.ml.OcrTextBlock

/** PP-OCR detector/recognizer implementation for the v0.3 mobile package. */
class OnnxOcrEngine private constructor(
    private val environment: OrtEnvironment,
    private val detector: OrtSession,
    private val recognizer: OrtSession,
    private val dictionary: List<String>
) : OcrEngine {
    override suspend fun recognize(image: OcrImage): List<OcrTextBlock> = withContext(Dispatchers.Default) {
        val source = Mat(image.height, image.width, CvType.CV_8UC3).apply {
            put(0, 0, image.bgrPixels)
        }
        try {
            val size = OcrPreprocessor.detectorSize(image.width, image.height)
            val resized = Mat()
            Imgproc.resize(source, resized, Size(size.width.toDouble(), size.height.toDouble()))
            try {
                val probability = detect(resized)
                try {
                    boxes(probability).mapNotNull { detectorBox ->
                        val box = detectorBox.scaled(1f / size.scale)
                        recognizeBox(source, box)?.let { text ->
                            val points = points(box)
                            OcrTextBlock(
                                text = text,
                                left = (points.minOf(Point::x) / image.width).toFloat().coerceIn(0f, 1f),
                                top = (points.minOf(Point::y) / image.height).toFloat().coerceIn(0f, 1f),
                                right = (points.maxOf(Point::x) / image.width).toFloat().coerceIn(0f, 1f),
                                bottom = (points.maxOf(Point::y) / image.height).toFloat().coerceIn(0f, 1f),
                                confidence = 1f
                            )
                        }
                    }
                } finally { probability.release() }
            } finally { resized.release() }
        } finally { source.release() }
    }

    private fun detect(image: Mat): Mat {
        val tensor = FloatArray(3 * image.rows() * image.cols())
        val pixel = ByteArray(3)
        for (y in 0 until image.rows()) for (x in 0 until image.cols()) {
            image.get(y, x, pixel)
            for (channel in 0..2) tensor[channel * image.rows() * image.cols() + y * image.cols() + x] =
                (((pixel[channel].toInt() and 0xff) / 255.0 - DETECTOR_MEAN[channel]) /
                    DETECTOR_STD[channel]).toFloat()
        }
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(tensor), longArrayOf(1, 3, image.rows().toLong(), image.cols().toLong())).use { input ->
            detector.run(mapOf("x" to input)).use { output ->
                @Suppress("UNCHECKED_CAST")
                val values = output[0].value as Array<Array<Array<FloatArray>>>
                val probability = FloatArray(image.rows() * image.cols())
                values[0][0].forEachIndexed { rowIndex, row ->
                    row.copyInto(probability, destinationOffset = rowIndex * image.cols())
                }
                return Mat(image.rows(), image.cols(), CvType.CV_32F).apply {
                    put(0, 0, probability)
                }
            }
        }
    }

    private fun boxes(probability: Mat): List<RotatedRect> {
        val binary = Mat()
        Imgproc.threshold(probability, binary, DETECTOR_THRESHOLD, 255.0, Imgproc.THRESH_BINARY)
        binary.convertTo(binary, CvType.CV_8U)
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(binary, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        binary.release()
        return contours.mapNotNull { contour ->
            val contour2f = MatOfPoint2f(*contour.toArray())
            val rect = Imgproc.minAreaRect(contour2f)
            contour.release()
            contour2f.release()
            if (minOf(rect.size.width, rect.size.height) < MIN_BOX_SIDE) null else rect.expanded()
        }.sortedWith(compareBy<RotatedRect> { it.center.y }.thenBy { it.center.x })
    }

    private fun recognizeBox(source: Mat, box: RotatedRect): String? {
        val sourcePoints = points(box)
        val width = maxOf(distance(sourcePoints[0], sourcePoints[1]), distance(sourcePoints[2], sourcePoints[3])).toInt().coerceAtLeast(1)
        val height = maxOf(distance(sourcePoints[0], sourcePoints[3]), distance(sourcePoints[1], sourcePoints[2])).toInt().coerceAtLeast(1)
        val crop = Mat()
        Imgproc.warpPerspective(source, crop, Imgproc.getPerspectiveTransform(MatOfPoint2f(*sourcePoints), MatOfPoint2f(Point(0.0, 0.0), Point(width.toDouble(), 0.0), Point(width.toDouble(), height.toDouble()), Point(0.0, height.toDouble()))), Size(width.toDouble(), height.toDouble()))
        try { return decode(crop) } finally { crop.release() }
    }

    private fun decode(crop: Mat): String? {
        val ratio = crop.cols().toFloat() / crop.rows().coerceAtLeast(1)
        val width = kotlin.math.ceil(48 * ratio).toInt().coerceIn(1, MAX_RECOGNIZER_WIDTH)
        val resized = Mat()
        Imgproc.resize(crop, resized, Size(width.toDouble(), 48.0))
        val values = FloatArray(3 * 48 * MAX_RECOGNIZER_WIDTH)
        val pixel = ByteArray(3)
        for (y in 0 until 48) for (x in 0 until width) {
            resized.get(y, x, pixel)
            for (c in 0..2) {
                values[c * 48 * MAX_RECOGNIZER_WIDTH + y * MAX_RECOGNIZER_WIDTH + x] =
                    ((pixel[c].toInt() and 0xff) / 255.0 * 2.0 - 1.0).toFloat()
            }
        }
        resized.release()
        return OnnxTensor.createTensor(environment, FloatBuffer.wrap(values), longArrayOf(1, 3, 48, MAX_RECOGNIZER_WIDTH.toLong())).use { input -> recognizer.run(mapOf("x" to input)).use { output ->
            @Suppress("UNCHECKED_CAST")
            val logits = (output[0].value as Array<Array<FloatArray>>)[0]
            var previous = -1
            buildString {
                logits.forEach { row ->
                    val current = row.indices.maxByOrNull(row::get) ?: return@forEach
                    if (current != 0 && current != previous) append(dictionary.getOrNull(current - 1))
                    previous = current
                }
            }.trim().ifBlank { null }
        } }
    }

    override fun close() { detector.close(); recognizer.close() }

    companion object {
        suspend fun open(modelPackage: TextModelPackage): OnnxOcrEngine? = withContext(Dispatchers.IO) {
            val det = modelPackage.components["ocr_det"] ?: return@withContext null
            val rec = modelPackage.components["ocr_rec"] ?: return@withContext null
            check(OpenCVLoader.initLocal()) { "OpenCV is unavailable" }
            val dictionary = rec.auxiliaryFiles["ocr/dict.txt"]?.readLines()?.filter(String::isNotBlank).orEmpty() + " "
            require(dictionary.isNotEmpty())
            val environment = OrtEnvironment.getEnvironment()
            OnnxOcrEngine(environment, environment.createSession(det.modelFile.absolutePath), environment.createSession(rec.modelFile.absolutePath), dictionary)
        }
        private val DETECTOR_MEAN = doubleArrayOf(.485, .456, .406); private val DETECTOR_STD = doubleArrayOf(.229, .224, .225)
        private const val DETECTOR_THRESHOLD = .3; private const val MIN_BOX_SIDE = 3.0; private const val MAX_RECOGNIZER_WIDTH = 2048
        private fun points(rect: RotatedRect): Array<Point> {
            val unordered = Array(4) { Point() }.also(rect::points)
            val top = unordered.sortedBy(Point::y).take(2).sortedBy(Point::x)
            val bottom = unordered.sortedByDescending(Point::y).take(2).sortedBy(Point::x)
            return arrayOf(top[0], top[1], bottom[1], bottom[0])
        }
        private fun distance(a: Point, b: Point): Double = kotlin.math.hypot(a.x - b.x, a.y - b.y)
        private fun RotatedRect.scaled(scale: Float) = RotatedRect(
            Point(center.x * scale, center.y * scale),
            Size(size.width * scale, size.height * scale),
            angle
        )
        private fun RotatedRect.expanded(): RotatedRect {
            val perimeter = 2.0 * (size.width + size.height)
            val offset = size.area() * 1.6 / perimeter.coerceAtLeast(1.0)
            return RotatedRect(center, Size(size.width + 2 * offset, size.height + 2 * offset), angle)
        }
    }
}
