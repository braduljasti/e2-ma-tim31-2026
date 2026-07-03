package com.example.slagalica.ui.main

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.media.RingtoneManager
import android.view.animation.OvershootInterpolator
import com.example.slagalica.databinding.DialogNagradaBinding
import com.example.slagalica.model.NagradaCiklusa
import com.example.slagalica.model.RangCiklus
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object NagradaAnimacija {

    fun prikazi(context: Context, nagrada: NagradaCiklusa) {
        val binding = DialogNagradaBinding.inflate(android.view.LayoutInflater.from(context))

        val nazivCiklusa = if (nagrada.ciklus == RangCiklus.NEDELJNI) "nedeljnoj" else "mesečnoj"
        binding.tvNagradaOpis.text = "Osvojili ste ${nagrada.mesto}. mjesto na $nazivCiklusa rang listi!"
        binding.tvNagradaTokeni.text = "🪙 +${nagrada.tokeni} tokena"

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setCancelable(true)
            .create()

        binding.btnNagradaZatvori.setOnClickListener { dialog.dismiss() }
        dialog.show()

        val scaleX = ObjectAnimator.ofFloat(binding.tvNagradaEmoji, "scaleX", 0f, 1.2f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.tvNagradaEmoji, "scaleY", 0f, 1.2f, 1f)
        val rotate = ObjectAnimator.ofFloat(binding.tvNagradaEmoji, "rotation", -20f, 20f, -10f, 10f, 0f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY, rotate)
            duration = 700L
            interpolator = OvershootInterpolator()
            start()
        }

        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
        }
    }
}
