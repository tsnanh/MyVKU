package dev.tsnanh.vku.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import dev.tsnanh.vku.R
import dev.tsnanh.vku.adapters.NoticeAdapter
import dev.tsnanh.vku.databinding.FragmentPageMakeupClassBinding
import dev.tsnanh.vku.domain.entities.Resource
import dev.tsnanh.vku.utils.showSnackbarWithAction
import dev.tsnanh.vku.viewmodels.PageMakeupClassViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi

@AndroidEntryPoint
class PageMakeupClassFragment : Fragment() {
    companion object {
        fun newInstance() = PageMakeupClassFragment()
    }

    private lateinit var binding: FragmentPageMakeupClassBinding
    private lateinit var adapterMakeupClass: NoticeAdapter
    private val viewModel: PageMakeupClassViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_page_makeup_class, container, false)
        return binding.root
    }

    @ExperimentalCoroutinesApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        adapterMakeupClass = NoticeAdapter(emptyList())

        binding.listMakeupClass.apply {
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
            adapter = adapterMakeupClass
        }

        viewModel.makeUpClass
            .observe(viewLifecycleOwner) { result ->
                when (result) {
                    is Resource.Error -> showSnackbarWithAction(requireView(),
                        result.message ?: "Error")
                    is Resource.Loading -> {
                    }
                    is Resource.Success -> {
                        result.data?.let { adapterMakeupClass.updateList(it) }
                    }
                }
            }
    }
}