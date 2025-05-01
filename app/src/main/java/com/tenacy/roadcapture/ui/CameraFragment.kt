package com.tenacy.roadcapture.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.tenacy.roadcapture.databinding.FragmentCameraBinding
import com.yalantis.ucrop.UCrop
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class CameraFragment : BaseFragment() {

    private var _binding: FragmentCameraBinding? = null
    val binding get() = _binding!!

    private val vm: CameraViewModel by viewModels()
    private val args: CameraFragmentArgs by navArgs()

    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private lateinit var cameraExecutor: ExecutorService
    private var capturedImageUri: Uri? = null

    // 크롭 결과를 처리하기 위한 ActivityResultLauncher
    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { intent ->
                val resultUri = UCrop.getOutput(intent)
                if (resultUri != null) {
                    // 크롭된 이미지 압축
                    val compressedUri = compressImage(resultUri)

                    // 압축된 이미지 URI 저장
                    capturedImageUri = compressedUri
                    vm.setCapturedImageUri(compressedUri)

                    // 압축된 이미지로 미리보기 업데이트
                    binding.imagePreview.setImageURI(compressedUri)
                    showCapturePreviewUI()
                }
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            result.data?.let { intent ->
                val error = UCrop.getError(intent)
                Log.e(TAG, "이미지 크롭 오류: $error")
                Toast.makeText(requireContext(), "이미지 크롭 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)

        binding.vm = vm
        binding.lifecycleOwner = viewLifecycleOwner

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCamera()
        setupUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }

    private fun setupCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
    }

    private fun setupUI() {
        // 촬영 버튼
        binding.btnCapture.setOnClickListener {
            takePhoto()
        }

        // 뒤로가기 버튼
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 사진 확인 화면 UI 이벤트
        binding.btnUsePhoto.setOnClickListener {
            // 사진 사용하기 선택
            capturedImageUri?.let { uri ->
                val address: TripFragment.Address = args.address
                findNavController().navigate(CameraFragmentDirections.actionCameraToNewMemory(address, uri))
            }
        }

        binding.btnRetakePhoto.setOnClickListener {
            // 다시 촬영하기
            showCameraUI()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 미리보기 설정
            preview = Preview.Builder().build()

            // 이미지 캡처 설정
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(Surface.ROTATION_90)
                .build()

            // 카메라 선택 (전면/후면)
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                // 카메라 바인딩 전에 기존 바인딩 해제
                cameraProvider.unbindAll()

                // 카메라와 라이프사이클 바인딩
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                // 미리보기를 PreviewView에 연결
                preview?.setSurfaceProvider(binding.previewView.surfaceProvider)
            } catch (e: Exception) {
                Log.e(TAG, "카메라 바인딩 실패", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // 이미지 파일 이름 설정
        val fileName = SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault())
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RoadCapture")
            }
        }

        // 저장 옵션 설정
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            requireContext().contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        // 사진 촬영
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri
                    if (savedUri != null) {
                        // 캡처된 이미지 URI 저장
                        capturedImageUri = savedUri
                        // 크롭 기능 호출
                        startCrop(savedUri)
                    } else {
                        Toast.makeText(requireContext(), "사진 저장 실패", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "사진 촬영 실패: ${exception.message}", exception)
                    Toast.makeText(
                        requireContext(),
                        "사진 촬영 실패: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun startCrop(sourceUri: Uri) {
        val destinationFileName = "cropped_${System.currentTimeMillis()}.jpg"
        val destinationUri = Uri.fromFile(File(requireContext().cacheDir, destinationFileName))

        val options = UCrop.Options().apply {
            setCompressionQuality(95)
            setHideBottomControls(false)
            setFreeStyleCropEnabled(false)
            // 4:3 비율만 선택 가능하도록 설정
            setAspectRatioOptions(0,
                com.yalantis.ucrop.model.AspectRatio("4:3", 4f, 3f))
            setToolbarTitle("이미지 자르기")
            setToolbarColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            setStatusBarColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            setToolbarWidgetColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            setRootViewBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.black))
        }

        val uCrop = UCrop.of(sourceUri, destinationUri)
            .withOptions(options)
            .withAspectRatio(4f, 3f) // 16:9 비율

        cropLauncher.launch(uCrop.getIntent(requireContext()))
    }

    private fun showCapturePreviewUI() {
        // 카메라 UI 숨기기
        binding.previewView.visibility = View.GONE
        binding.cameraControlsLayout.visibility = View.GONE

        // 미리보기 UI 표시
        binding.capturePreviewLayout.visibility = View.VISIBLE

        // 미리보기 이미지 설정
        binding.imagePreview.setImageURI(capturedImageUri)
    }

    private fun showCameraUI() {
        // 미리보기 UI 숨기기
        binding.capturePreviewLayout.visibility = View.GONE

        // 카메라 UI 다시 표시
        binding.previewView.visibility = View.VISIBLE
        binding.cameraControlsLayout.visibility = View.VISIBLE
    }

    private fun compressImage(sourceUri: Uri): Uri {
        val fileName = "compressed_${System.currentTimeMillis()}.jpg"
        val compressedFile = File(requireContext().cacheDir, fileName)

        try {
            // 이미지를 비트맵으로 로드
            val inputStream = requireContext().contentResolver.openInputStream(sourceUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // 비트맵 압축 (JPEG 포맷, 10% 품질로 압축)
            val outputStream = FileOutputStream(compressedFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 30, outputStream) // 10%로 압축
            outputStream.flush()
            outputStream.close()

            // 압축된 파일 크기 로그 출력 (디버깅용)
            val originalSize = requireContext().contentResolver.openFileDescriptor(sourceUri, "r")?.statSize ?: 0
            val compressedSize = compressedFile.length()
            Log.d(TAG, "원본 크기: $originalSize 바이트, 압축 크기: $compressedSize 바이트, 압축률: ${compressedSize.toFloat() / originalSize.toFloat() * 100}%")

            // 압축된 파일의 URI 반환
            return Uri.fromFile(compressedFile)
        } catch (e: Exception) {
            Log.e(TAG, "이미지 압축 실패: ${e.message}", e)
            // 압축 실패 시 원본 URI 반환
            return sourceUri
        }
    }

    companion object {
        private const val TAG = "CameraFragment"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}