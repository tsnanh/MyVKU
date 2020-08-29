package dev.tsnanh.vku.views

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.tsnanh.vku.R
import dev.tsnanh.vku.adapters.AttachmentAdapter
import dev.tsnanh.vku.adapters.AttachmentClickListener
import dev.tsnanh.vku.adapters.NewsAdapter
import dev.tsnanh.vku.adapters.NewsClickListener
import dev.tsnanh.vku.databinding.AttachmentDialogLayoutBinding
import dev.tsnanh.vku.databinding.FragmentPageNewsBinding
import dev.tsnanh.vku.domain.constants.SecretConstants
import dev.tsnanh.vku.domain.entities.News
import dev.tsnanh.vku.receivers.AttachmentReceiver
import dev.tsnanh.vku.utils.Constants
import dev.tsnanh.vku.utils.CustomTabHelper
import dev.tsnanh.vku.utils.showSnackbarWithAction
import dev.tsnanh.vku.viewmodels.PageNewsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import timber.log.Timber
import java.net.URLConnection
import javax.inject.Inject

@AndroidEntryPoint
class PageNewsFragment : Fragment() {

    companion object {
        fun newInstance() = PageNewsFragment()
    }

    private val viewModel: PageNewsViewModel by viewModels()
    private lateinit var binding: FragmentPageNewsBinding
    private val customTabHelper = CustomTabHelper()
    private lateinit var adapterNews: NewsAdapter

    @Inject
    lateinit var preferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_page_news, container, false)

        return binding.root
    }

    @ExperimentalCoroutinesApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        adapterNews = NewsAdapter(NewsClickListener(
            viewClickListener = this::launchNews,
            shareClickListener = { news ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, news.title)
                    putExtra(Intent.EXTRA_TEXT, "${news.content?.take(30)}...")
                }
                startActivity(Intent.createChooser(intent, "Share via"))
            }
        ))

        binding.listNews.apply {
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
            adapter = adapterNews
        }
        viewModel.news
            .observe<List<News>>(viewLifecycleOwner) { result ->
                adapterNews.submitList(result)
            }
    }

    private fun downloadAndOpenFile(it: String) {
        val downloadManager =
            requireContext().getSystemService<DownloadManager>()
        val fileNameMap = URLConnection.getFileNameMap()
        val request = DownloadManager.Request("${Constants.DAO_TAO_UPLOAD_URL}/$it".toUri())
            .setTitle(it)
            .setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_MOBILE or
                        DownloadManager.Request.NETWORK_WIFI
            )
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                it
            )
            .setMimeType(
                fileNameMap.getContentTypeFor(it)
            )
        requireActivity().registerReceiver(
            AttachmentReceiver(),
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
        downloadManager?.enqueue(request)
    }

    private fun launchNews(news: News) {
        val url = SecretConstants.SINGLE_NEWS_URL(news.cmsId)
        val builder = CustomTabsIntent.Builder()
        builder.setToolbarColor(ContextCompat.getColor(requireContext(), R.color.primaryColor))
        builder.addDefaultShareMenuItem()
        builder.setShowTitle(true)
        builder.setStartAnimations(
            requireActivity(),
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
        builder.setExitAnimations(
            requireActivity(),
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
        builder.setColorScheme(
            when (preferences.getString(
                getString(R.string.night_mode_key),
                Constants.MODE_SYSTEM
            )) {
                Constants.MODE_DARK -> CustomTabsIntent.COLOR_SCHEME_DARK
                Constants.MODE_LIGHT -> CustomTabsIntent.COLOR_SCHEME_LIGHT
                else -> CustomTabsIntent.COLOR_SCHEME_SYSTEM
            }
        )
        val customTabsIntent = builder.build()

        // check is chrome available
        val packageName = customTabHelper.getPackageNameToUse(requireActivity(), url)

        if (packageName == null) {
            findNavController().navigate(
                NewsFragmentDirections.actionNavigationNewsToActivityNews(
                    SecretConstants.SINGLE_NEWS_URL(news.cmsId), news.title!!
                )
            )
        } else {
            customTabsIntent.intent.setPackage(packageName)
            customTabsIntent.launchUrl(requireActivity(), Uri.parse(url))
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val news = adapterNews.currentList[item.itemId]
        when (item.order) {
            0 -> launchNews(news)
            1 -> {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(
                            requireActivity(),
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    ) {
                        with(requireContext()) {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle(getString(R.string.msg_permission_required))
                                .setMessage(getString(R.string.msg_need_permission))
                                .setPositiveButton(getString(R.string.text_ok)) { d, _ ->
                                    requestPermissions(
                                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                        Constants.RC_PERMISSION
                                    )
                                    d.dismiss()
                                }
                                .setNegativeButton(getString(R.string.text_cancel)) { d, _ ->
                                    d.dismiss()
                                }
                                .create()
                                .show()
                        }
                    } else {
                        requestPermissions(
                            arrayOf(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ),
                            Constants.RC_PERMISSION
                        )
                    }
                } else {
                    if (!news.attachment.isNullOrBlank()) {
                        val files = news.attachment
                            ?.removeSuffix("||")
                            ?.split("||")
                        Timber.d(files.toString())

                        val attachmentBinding =
                            AttachmentDialogLayoutBinding.inflate(LayoutInflater.from(requireContext()))
                        if (!files.isNullOrEmpty()) {
                            val builder = MaterialAlertDialogBuilder(requireContext())
                                .setView(attachmentBinding.root)
                                .setTitle("Attachment")
                                .create()
                            builder.show()
                            val attachmentAdapter =
                                AttachmentAdapter(files, AttachmentClickListener {
                                    downloadAndOpenFile(it)
                                })
                            attachmentBinding.listFiles.apply {
                                adapter = attachmentAdapter
                                setHasFixedSize(true)
                                layoutManager = LinearLayoutManager(requireContext())
                            }
                        }
                    } else {
                        showSnackbarWithAction(binding.root, getString(R.string.text_no_attachment))
                    }
                }
            }
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Constants.RC_PERMISSION && resultCode == Activity.RESULT_OK) {
            showSnackbarWithAction(
                requireView(),
                requireContext().getString(R.string.msg_permission_granted)
            )
        }
    }
}