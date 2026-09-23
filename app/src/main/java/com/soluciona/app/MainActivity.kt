package com.soluciona.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SolucionaTheme { SolucionaApp(appViewModel) }
        }
    }
}

private val categories = listOf(
    "⚡" to "Electricidad",
    "🚰" to "Plomería",
    "🔥" to "Gas",
    "❄️" to "Climatización",
    "🔑" to "Cerrajería",
    "🧱" to "Construcción"
)
private val zones = listOf("Carcarañá", "Rosario", "San Lorenzo", "Roldán", "Funes", "Cañada de Gómez")

@Composable
fun SolucionaApp(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val start = when (vm.currentUser?.role) {
        UserRole.CLIENTE -> "client/home"
        UserRole.PRESTADOR -> "provider/dashboard"
        null -> "welcome"
    }
    NavHost(navController = nav, startDestination = start) {
        composable("welcome") { WelcomeScreen(nav) }
        composable("login") { LoginScreen(nav, vm) }
        composable("register/client") { RegisterScreen(nav, vm, UserRole.CLIENTE) }
        composable("register/provider") { RegisterScreen(nav, vm, UserRole.PRESTADOR) }
        composable("client/home") { ClientHomeScreen(nav, vm) }
        composable("client/providers") { ProvidersScreen(nav, vm) }
        composable("client/confirm") { ConfirmScreen(nav, vm) }
        composable("client/success") { SuccessScreen(nav, vm) }
        composable("requests") { RequestsScreen(nav, vm) }
        composable("request/detail") { RequestDetailScreen(nav, vm) }
        composable("request/chat") { ChatScreen(nav, vm) }
        composable("provider/dashboard") { ProviderDashboardScreen(nav, vm) }
        composable("provider/profile") { ProviderProfileScreen(nav, vm) }
        composable("account") { AccountScreen(nav, vm) }
    }
}

@Composable
private fun ScreenFrame(
    title: String? = null,
    subtitle: String? = null,
    back: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(AppBg).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (back != null) {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
            }
            Text("Soluciona.", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Ink)
            Spacer(Modifier.weight(1f))
            Surface(color = SoftBlue, shape = RoundedCornerShape(50)) {
                Text("Prototipo", color = BrandBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        if (title != null) {
            Spacer(Modifier.height(22.dp))
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        }
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = Muted, fontSize = 15.sp)
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, icon: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        if (icon != null) { icon(); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WelcomeScreen(nav: NavHostController) {
    ScreenFrame {
        Spacer(Modifier.height(50.dp))
        Surface(shape = CircleShape, color = SoftBlue, modifier = Modifier.size(88.dp).align(Alignment.CenterHorizontally)) {
            Box(contentAlignment = Alignment.Center) { Text("✓", color = Success, fontSize = 48.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(24.dp))
        Text("Soluciones confiables, cerca tuyo", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(10.dp))
        Text("Conectamos personas que necesitan resolver un problema con profesionales verificados de su zona.", color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(32.dp))
        PrimaryButton("Necesito un servicio") { nav.navigate("register/client") }
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Quiero ofrecer mis servicios") { nav.navigate("register/provider") }
        Spacer(Modifier.height(18.dp))
        TextButton(onClick = { nav.navigate("login") }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Ya tengo cuenta · Iniciar sesión", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        Text("MVP local · listo para conectar a un backend", color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp))
    }
}

@Composable
private fun LoginScreen(nav: NavHostController, vm: AppViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    ScreenFrame("Ingresar", "Accedé como cliente o prestador.", back = { nav.popBackStack() }) {
        OutlinedTextField(email, { email = it; vm.clearAuthError() }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it; vm.clearAuthError() }, label = { Text("Contraseña") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
        vm.authError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Ingresar") {
            if (vm.login(email, password)) {
                nav.navigate(if (vm.currentUser?.role == UserRole.CLIENTE) "client/home" else "provider/dashboard") { popUpTo("welcome") { inclusive = true } }
            }
        }
        Spacer(Modifier.height(24.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Cuentas demo", fontWeight = FontWeight.Bold)
                Text("Cliente: cliente@soluciona.app / demo1234", color = Muted, fontSize = 13.sp)
                Text("Prestador: martin@soluciona.app / demo1234", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun RegisterScreen(nav: NavHostController, vm: AppViewModel, role: UserRole) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf("Carcarañá") }
    var license by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("35000") }
    var selectedServices by remember { mutableStateOf(setOf("Electricidad")) }
    val title = if (role == UserRole.CLIENTE) "Crear cuenta de cliente" else "Registrarme como prestador"
    ScreenFrame(title, "Tus datos quedan guardados localmente en este MVP.", back = { nav.popBackStack() }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre y apellido") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(email, { email = it; vm.clearAuthError() }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(password, { password = it; vm.clearAuthError() }, label = { Text("Contraseña · mínimo 6 caracteres") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            Spacer(Modifier.height(12.dp))
            ZoneSelector(zone) { zone = it }
            if (role == UserRole.PRESTADOR) {
                Spacer(Modifier.height(16.dp))
                Text("Servicios que ofrecés", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                categories.forEach { (_, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = label in selectedServices, onCheckedChange = { checked -> selectedServices = if (checked) selectedServices + label else selectedServices - label })
                        Text(label)
                    }
                }
                OutlinedTextField(license, { license = it }, label = { Text("Matrícula / habilitación (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(priceText, { priceText = it.filter(Char::isDigit) }, label = { Text("Valor visita / diagnóstico") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            vm.authError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Crear cuenta") {
                val ok = vm.register(name, email, password, phone, zone, role, selectedServices.toList(), license, priceText.toIntOrNull() ?: 35000)
                if (ok) nav.navigate(if (role == UserRole.CLIENTE) "client/home" else "provider/dashboard") { popUpTo("welcome") { inclusive = true } }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ZoneSelector(zone: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(6.dp)); Text("Zona: $zone"); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            zones.forEach { z -> DropdownMenuItem(text = { Text(z) }, onClick = { onSelect(z); expanded = false }) }
        }
    }
}

@Composable
private fun ClientHomeScreen(nav: NavHostController, vm: AppViewModel) {
    var detail by remember { mutableStateOf(vm.draft.detail) }
    var selected by remember { mutableStateOf(vm.draft.category) }
    var zone by remember { mutableStateOf(vm.currentUser?.zone ?: vm.draft.zone) }
    ScreenFrame("¿Qué necesitás solucionar?", "Contanos qué pasa. Nosotros buscamos el profesional adecuado.") {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = detail,
                onValueChange = { detail = it },
                placeholder = { Text("Ej.: Tengo una pérdida de agua debajo de la pileta...") },
                modifier = Modifier.fillMaxWidth().height(118.dp),
                maxLines = 5
            )
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxWidth().height(214.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
                items(categories) { (emoji, label) ->
                    val active = selected == label
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(64.dp).clickable { selected = label }.then(if (active) Modifier.border(2.dp, BrandBlue, RoundedCornerShape(12.dp)) else Modifier),
                        shape = RoundedCornerShape(12.dp), color = Color.White
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("$emoji $label", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(categoryHint(label), color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ZoneSelector(zone) { zone = it }
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Continuar →") {
                vm.draft = RequestDraft(selected, detail, zone)
                vm.selectedProviderId = null
                nav.navigate("client/providers")
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nav.navigate("requests") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.List, null); Spacer(Modifier.width(4.dp)); Text("Mis solicitudes") }
                OutlinedButton(onClick = { nav.navigate("account") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Person, null); Spacer(Modifier.width(4.dp)); Text("Cuenta") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun categoryHint(label: String) = when (label) {
    "Electricidad" -> "Luces, enchufes, cortes"
    "Plomería" -> "Pérdidas, cañerías"
    "Gas" -> "Solo matriculados"
    "Climatización" -> "Aire y refrigeración"
    "Cerrajería" -> "Puertas, cerraduras"
    else -> "Obra y reparaciones"
}

@Composable
private fun ProvidersScreen(nav: NavHostController, vm: AppViewModel) {
    val list = vm.providersForDraft()
    LaunchedEffect(list.map { it.userId }) {
        if (vm.selectedProviderId == null || list.none { it.userId == vm.selectedProviderId }) {
            list.firstOrNull()?.let { vm.selectProvider(it.userId) }
        }
    }
    ScreenFrame("Encontramos soluciones", "Profesionales disponibles para ${vm.draft.zone}.", back = { nav.popBackStack() }) {
        Surface(color = SoftBlue, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text("${categories.firstOrNull { it.second == vm.draft.category }?.first ?: "🛠️"} ${vm.draft.category}", modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) {
            EmptyState("No encontramos prestadores para esta categoría todavía.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(list) { provider -> ProviderCard(provider, selected = vm.selectedProviderId == provider.userId) { vm.selectProvider(provider.userId) } }
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Elegir profesional →", onClick = { nav.navigate("client/confirm") }, enabled = vm.selectedProviderId != null)
            Spacer(Modifier.height(8.dp))
            SecondaryButton("← Modificar problema") { nav.popBackStack() }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ProviderCard(provider: ProviderProfile, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).then(if (selected) Modifier.border(2.dp, BrandBlue, RoundedCornerShape(14.dp)) else Modifier),
        shape = RoundedCornerShape(14.dp), color = Color.White
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Color(0xFFF0F2F6), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(provider.name.take(1), fontWeight = FontWeight.ExtraBold) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(provider.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(if (provider.verified) "✓ Identidad y matrícula verificadas" else "Perfil pendiente de verificación", color = if (provider.verified) Success else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("⭐ ${provider.rating} · ${provider.jobs} trabajos · ${provider.distanceKm} km", color = Muted, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Desde ${money(provider.baseVisit)}", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text("Visita incluida", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ConfirmScreen(nav: NavHostController, vm: AppViewModel) {
    val provider = vm.selectedProvider()
    if (provider == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    ScreenFrame("¡Casi solucionado!", "Revisá el resumen y confirmá la visita.", back = { nav.popBackStack() }) {
        Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Tu solicitud", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(12.dp))
                Surface(color = SoftBlue, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Problema", fontWeight = FontWeight.Bold)
                        Text("${vm.draft.category} — ${vm.draft.detail.ifBlank { "sin detalle adicional" }}", color = Muted)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(provider.name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text("✓ ${if (provider.verified) "Profesional verificado" else "Perfil registrado"} · ⭐ ${provider.rating}", color = Muted)
                HorizontalDivider(Modifier.padding(vertical = 14.dp))
                Row {
                    Column(Modifier.weight(1f)) { Text("Visita / diagnóstico", color = Muted, fontSize = 12.sp); Text(money(provider.baseVisit), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
                    Column(horizontalAlignment = Alignment.End) { Text("Protección", color = Muted, fontSize = 12.sp); Text("Incluida", fontWeight = FontWeight.ExtraBold) }
                }
                Spacer(Modifier.height(12.dp))
                Text("🔒 El precio final del trabajo se confirma antes de comenzar. No hay sorpresas sin tu aprobación.", color = Muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Confirmar visita") {
            vm.createRequest()
            nav.navigate("client/success") { popUpTo("client/home") }
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton("← Elegir otro profesional") { nav.popBackStack() }
    }
}

@Composable
private fun SuccessScreen(nav: NavHostController, vm: AppViewModel) {
    val request = vm.requestById(vm.lastRequestId)
    ScreenFrame {
        Spacer(Modifier.height(36.dp))
        Surface(shape = CircleShape, color = Color(0xFFDDFBE8), modifier = Modifier.size(82.dp).align(Alignment.CenterHorizontally)) {
            Box(contentAlignment = Alignment.Center) { Text("✓", color = Success, fontSize = 48.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(18.dp))
        Text("Solicitud confirmada", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("La visita con ${request?.providerName ?: "el profesional"} quedó solicitada. El profesional podrá confirmar el horario.", color = Muted, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(22.dp))
        StatusCard(request?.status ?: RequestStatus.SOLICITADO)
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Ver mis solicitudes") { nav.navigate("requests") }
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Nueva solicitud") { nav.navigate("client/home") { popUpTo("client/home") { inclusive = true } } }
    }
}

@Composable
private fun StatusCard(status: RequestStatus) {
    val steps = listOf(RequestStatus.SOLICITADO, RequestStatus.CONFIRMADO, RequestStatus.EN_CAMINO, RequestStatus.FINALIZADO)
    val current = steps.indexOf(status).coerceAtLeast(0)
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Estado del servicio", fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.forEachIndexed { i, _ -> Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(8.dp)).background(if (i <= current) Success else Color(0xFFD8DDE6))) }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                listOf("Solicitado", "Confirmado", "En camino", "Finalizado").forEach { Text(it, color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RequestsScreen(nav: NavHostController, vm: AppViewModel) {
    val user = vm.currentUser
    val list = vm.myRequests()
    ScreenFrame("Mis solicitudes", if (user?.role == UserRole.CLIENTE) "Seguí el estado de tus servicios." else "Trabajos vinculados a tu perfil.", back = { nav.popBackStack() }) {
        if (list.isEmpty()) {
            EmptyState(if (user?.role == UserRole.CLIENTE) "Todavía no hiciste solicitudes." else "Todavía no recibiste trabajos.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(list) { req ->
                    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { vm.lastRequestId = req.id; nav.navigate("request/detail") }) {
                        Column(Modifier.padding(14.dp)) {
                            Row { Text(req.category, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); StatusPill(req.status) }
                            Spacer(Modifier.height(6.dp))
                            Text(if (user?.role == UserRole.CLIENTE) req.providerName else req.clientName, color = Muted)
                            Text(req.detail, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(formatDate(req.createdAt), color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestDetailScreen(nav: NavHostController, vm: AppViewModel) {
    val req = vm.requestById(vm.lastRequestId)
    val user = vm.currentUser
    if (req == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return }
    val context = LocalContext.current
    val provider = vm.providers.firstOrNull { it.userId == req.providerId }
    ScreenFrame("Detalle del servicio", "${req.category} · ${req.zone}", back = { nav.popBackStack() }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            StatusCard(req.status)
            Spacer(Modifier.height(12.dp))
            Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (user?.role == UserRole.CLIENTE) req.providerName else req.clientName, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                    Text(req.detail, color = Muted, modifier = Modifier.padding(top = 4.dp))
                    Text("Visita: ${money(req.visitPrice)}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Abrir chat") { nav.navigate("request/chat") }
            Spacer(Modifier.height(8.dp))
            if (user?.role == UserRole.CLIENTE && !provider?.phone.isNullOrBlank()) {
                SecondaryButton("Llamar al profesional") {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${provider?.phone}")))
                }
                Spacer(Modifier.height(8.dp))
            }
            if (user?.role == UserRole.PRESTADOR) {
                when (req.status) {
                    RequestStatus.SOLICITADO -> PrimaryButton("Confirmar trabajo") { vm.setRequestStatus(req.id, RequestStatus.CONFIRMADO) }
                    RequestStatus.CONFIRMADO -> PrimaryButton("Marcar: en camino") { vm.setRequestStatus(req.id, RequestStatus.EN_CAMINO) }
                    RequestStatus.EN_CAMINO -> PrimaryButton("Finalizar trabajo") { vm.setRequestStatus(req.id, RequestStatus.FINALIZADO) }
                    else -> Unit
                }
            } else if (req.status == RequestStatus.FINALIZADO && req.rating == null) {
                Spacer(Modifier.height(12.dp)); Text("Calificá el trabajo", fontWeight = FontWeight.Bold)
                Row { (1..5).forEach { star -> Text("⭐", fontSize = 30.sp, modifier = Modifier.clickable { vm.rateRequest(req.id, star) }.padding(4.dp)) } }
            } else if (req.rating != null) {
                Text("Tu calificación: ${"⭐".repeat(req.rating)}", modifier = Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProviderDashboardScreen(nav: NavHostController, vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.refresh() }
    val pending = vm.myRequests()
    ScreenFrame("Panel del prestador", "Gestioná solicitudes, estados y contacto con clientes.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("Pendientes", pending.count { it.status == RequestStatus.SOLICITADO }.toString(), Modifier.weight(1f))
            MiniStat("Activos", pending.count { it.status == RequestStatus.CONFIRMADO || it.status == RequestStatus.EN_CAMINO }.toString(), Modifier.weight(1f))
            MiniStat("Finalizados", pending.count { it.status == RequestStatus.FINALIZADO }.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { nav.navigate("provider/profile") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Build, null); Spacer(Modifier.width(4.dp)); Text("Mi perfil") }
            OutlinedButton(onClick = { nav.navigate("account") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Person, null); Spacer(Modifier.width(4.dp)); Text("Cuenta") }
        }
        Spacer(Modifier.height(14.dp))
        Text("Trabajos", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        if (pending.isEmpty()) {
            EmptyState("Cuando un cliente te elija, la solicitud aparecerá acá.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(pending) { req ->
                    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { vm.lastRequestId = req.id; nav.navigate("request/detail") }) {
                        Column(Modifier.padding(14.dp)) {
                            Row { Text(req.clientName, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); StatusPill(req.status) }
                            Text("${req.category} · ${req.zone}", color = Muted)
                            Text(req.detail, color = Muted, fontSize = 13.sp, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text(label, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ProviderProfileScreen(nav: NavHostController, vm: AppViewModel) {
    val p = vm.myProviderProfile()
    if (p == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return }
    var zone by remember { mutableStateOf(p.zone) }
    var license by remember { mutableStateOf(p.license) }
    var bio by remember { mutableStateOf(p.bio) }
    var price by remember { mutableStateOf(p.baseVisit.toString()) }
    var selected by remember { mutableStateOf(p.services.toSet()) }
    var saved by remember { mutableStateOf(false) }
    ScreenFrame("Mi perfil profesional", "Esta información es la que ven los clientes.", back = { nav.popBackStack() }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Servicios", fontWeight = FontWeight.Bold)
            categories.forEach { (_, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(label in selected, { checked -> selected = if (checked) selected + label else selected - label })
                    Text(label)
                }
            }
            ZoneSelector(zone) { zone = it }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(license, { license = it }, label = { Text("Matrícula / habilitación") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(bio, { bio = it }, label = { Text("Descripción profesional") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(price, { price = it.filter(Char::isDigit) }, label = { Text("Visita / diagnóstico") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Guardar cambios") {
                vm.updateMyProviderProfile(selected.toList(), zone, license, bio, price.toIntOrNull() ?: p.baseVisit)
                saved = true
            }
            if (saved) Text("✓ Perfil actualizado", color = Success, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChatScreen(nav: NavHostController, vm: AppViewModel) {
    val req = vm.requestById(vm.lastRequestId)
    if (req == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return }
    var text by remember { mutableStateOf("") }
    val messages = vm.messagesFor(req.id)
    ScreenFrame("Chat del servicio", "${req.category} · ${if (vm.currentUser?.role == UserRole.CLIENTE) req.providerName else req.clientName}", back = { nav.popBackStack() }) {
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(24.dp)) { Text("Escribí un mensaje para coordinar horarios o detalles del trabajo.", color = Muted) }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(messages) { msg ->
                        val mine = msg.senderId == vm.currentUser?.id
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                            Surface(color = if (mine) SoftBlue else Color(0xFFF0F2F6), shape = RoundedCornerShape(14.dp), modifier = Modifier.widthIn(max = 280.dp)) {
                                Column(Modifier.padding(10.dp)) { Text(msg.senderName, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(msg.text) }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, placeholder = { Text("Mensaje") }, modifier = Modifier.weight(1f), singleLine = true)
            IconButton(onClick = { vm.sendMessage(req.id, text); text = "" }, enabled = text.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = BrandBlue) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun AccountScreen(nav: NavHostController, vm: AppViewModel) {
    val u = vm.currentUser
    ScreenFrame("Mi cuenta", "Datos de la sesión local.", back = { nav.popBackStack() }) {
        Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text(u?.name ?: "", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(u?.email ?: "", color = Muted)
                Text("${if (u?.role == UserRole.CLIENTE) "Cliente" else "Prestador"} · ${u?.zone ?: ""}", color = Muted, modifier = Modifier.padding(top = 4.dp))
                if (!u?.phone.isNullOrBlank()) Text("Tel. ${u?.phone}", color = Muted)
            }
        }
        Spacer(Modifier.height(16.dp))
        SecondaryButton("Cerrar sesión") {
            vm.logout()
            nav.navigate("welcome") { popUpTo(0) }
        }
    }
}

@Composable
private fun StatusPill(status: RequestStatus) {
    val text = when (status) {
        RequestStatus.SOLICITADO -> "Solicitado"
        RequestStatus.CONFIRMADO -> "Confirmado"
        RequestStatus.EN_CAMINO -> "En camino"
        RequestStatus.FINALIZADO -> "Finalizado"
        RequestStatus.CANCELADO -> "Cancelado"
    }
    val color = if (status == RequestStatus.FINALIZADO) Success else BrandBlue
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) { Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) }
}

@Composable
private fun EmptyState(text: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp)); Text(text, color = Muted)
        }
    }
}

private fun money(value: Int): String = NumberFormat.getCurrencyInstance(Locale("es", "AR")).format(value).replace(",00", "")
private fun formatDate(value: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "AR")).format(Date(value))
