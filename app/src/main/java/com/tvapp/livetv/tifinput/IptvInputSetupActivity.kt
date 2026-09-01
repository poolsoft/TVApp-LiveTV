package com.tvapp.livetv.tifinput

import android.app.Activity
import android.content.Intent
import android.media.tv.TvInputInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tvapp.livetv.R
import com.tvapp.livetv.databinding.ActivityIptvInputSetupBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IptvInputSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIptvInputSetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIptvInputSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.sync.setOnClickListener { syncChannels() }
        binding.done.setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }
        binding.sync.requestFocus()
        syncChannels()
    }

    private fun syncChannels() {
        val inputId = intent.getStringExtra(TvInputInfo.EXTRA_INPUT_ID)
            ?: IptvInputResolver.findInputId(this)
        if (inputId == null) {
            binding.status.setText(R.string.iptv_input_not_found)
            return
        }
        binding.sync.isEnabled = false
        binding.status.setText(R.string.iptv_input_syncing)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    IptvInputChannelSyncRepository(this@IptvInputSetupActivity).sync(inputId)
                }
            }.onSuccess { result ->
                binding.status.text = getString(
                    R.string.iptv_input_sync_complete,
                    result.synced,
                    result.removed,
                )
                setResult(
                    Activity.RESULT_OK,
                    Intent().putExtra(TvInputInfo.EXTRA_INPUT_ID, inputId),
                )
                IptvInputSyncScheduler.schedulePeriodic(this@IptvInputSetupActivity)
            }.onFailure { error ->
                binding.status.text = getString(
                    R.string.iptv_input_sync_failed,
                    error.message ?: error.javaClass.simpleName,
                )
            }
            binding.sync.isEnabled = true
            binding.sync.requestFocus()
        }
    }
}
