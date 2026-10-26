package br.com.asa.smartpos.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.asa.smartpos.R
import br.com.asa.smartpos.ui.theme.*

@Composable
internal fun StartupSplashScreen() {
    Box(Modifier.fillMaxSize().background(AsaCharcoal)) {
        Image(
            painter = painterResource(R.drawable.asa_splash_mountains),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.78f,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.asa_logo),
                contentDescription = "Logo ASA",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 104.dp, height = 116.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "GERANDO VALOR",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "ATRAVÉS DE GERAÇÕES",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 11.sp,
                letterSpacing = 2.4.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
