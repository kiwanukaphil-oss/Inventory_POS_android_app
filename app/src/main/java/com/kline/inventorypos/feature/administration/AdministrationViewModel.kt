package com.kline.inventorypos.feature.administration
import androidx.lifecycle.*
import com.kline.inventorypos.app.AppContainer
import com.kline.inventorypos.core.model.AdministrationWorkspace
import com.kline.inventorypos.core.session.PosSession
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
data class AdministrationUiState(val workspace:AdministrationWorkspace=AdministrationWorkspace(null,emptyList(),emptyList()),val loading:Boolean=false,val error:String?=null)
class AdministrationViewModel(private val repository:com.kline.inventorypos.data.administration.AdministrationRepository):ViewModel(){private val state=MutableStateFlow(AdministrationUiState());val uiState=state.asStateFlow();private var key:String?=null;private var permissions=Triple(false,false,false);fun bindSession(s:PosSession){val k="${s.user.id}:${s.branch.id}";if(k==key)return;key=k;permissions=Triple(s.user.hasPermission("settings.view")||s.user.hasPermission("settings.edit"),s.user.hasPermission("branches.view"),s.user.hasPermission("users.view"));refresh()};fun refresh(){if(state.value.loading)return;val k=key?:return;viewModelScope.launch{state.update{it.copy(loading=true,error=null)};runCatching{repository.workspace(permissions.first,permissions.second,permissions.third)}.onSuccess{w->state.update{if(key==k)it.copy(workspace=w,loading=false)else it}}.onFailure{e->state.update{it.copy(loading=false,error=e.message)}}}};fun clearError()=state.update{it.copy(error=null)};class Factory(private val c:AppContainer):ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST")override fun<T:ViewModel>create(modelClass:Class<T>)=AdministrationViewModel(c.administrationRepository)as T}}
