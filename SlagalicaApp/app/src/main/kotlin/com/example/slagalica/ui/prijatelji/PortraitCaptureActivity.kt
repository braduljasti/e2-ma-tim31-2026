package com.example.slagalica.ui.prijatelji

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * QR skener zaključan u portret orijentaciji. zxing-android-embedded podrazumijevano
 * otvara skener u pejzažu; ova podklasa + `screenOrientation="portrait"` u manifestu
 * drži kameru uspravno.
 */
class PortraitCaptureActivity : CaptureActivity()
