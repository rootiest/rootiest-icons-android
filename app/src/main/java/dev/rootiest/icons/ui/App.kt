package dev.rootiest.icons.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import dev.rootiest.icons.IconsViewModel
import dev.rootiest.icons.Route

// The app: one screen at a time, from the view model's stack; Back pops it.
@Composable
fun IconsApp(vm: IconsViewModel) {
    BackHandler(enabled = vm.stack.size > 1, onBack = vm::back)
    val route = vm.stack.last()
    // a key per place in the stack, so a screen opened from another like it starts with its own state
    key(vm.stack.size, route) {
        when (route) {
            Route.Catalogue -> CatalogueScreen(vm)
            is Route.Detail -> DetailScreen(vm, route)
            Route.Own -> OwnScreen(vm)
            Route.Settings -> SettingsScreen(vm)
            Route.Licenses -> LicensesScreen(vm::back)
        }
    }
}
