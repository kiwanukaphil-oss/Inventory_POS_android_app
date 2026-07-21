package com.kline.inventorypos.feature.report
import androidx.lifecycle.*
import com.kline.inventorypos.app.AppContainer
import com.kline.inventorypos.core.model.ManagementReportWorkspace
import com.kline.inventorypos.core.session.PosSession
import java.time.LocalDate
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
enum class ReportPeriod(val label:String){Today("Today"),Last7("7 days"),Last30("30 days"),ThisMonth("This month")}
data class ManagementReportUiState(val period:ReportPeriod=ReportPeriod.Last7,val workspace:ManagementReportWorkspace=ManagementReportWorkspace(null,null),val loading:Boolean=false,val error:String?=null)
class ManagementReportViewModel(private val repository:com.kline.inventorypos.data.report.ManagementReportRepository):ViewModel(){private val state=MutableStateFlow(ManagementReportUiState());val uiState=state.asStateFlow();private var key:String?=null;private var permissions=false to false;fun bindSession(s:PosSession){val k="${s.user.id}:${s.branch.id}";if(k==key)return;key=k;permissions=s.user.hasPermission("reports.sales") to s.user.hasPermission("reports.financial");refresh()};fun setPeriod(p:ReportPeriod){if(p==state.value.period)return;state.update{it.copy(period=p,loading=false)};refresh()};fun refresh(){val snapshot=state.value;val k=key?:return;if(snapshot.loading)return;val(from,to)=snapshot.period.range();viewModelScope.launch{state.update{it.copy(loading=true,error=null)};runCatching{repository.load(from,to,permissions.first,permissions.second)}.onSuccess{w->state.update{if(key==k&&it.period==snapshot.period)it.copy(workspace=w,loading=false)else it}}.onFailure{e->state.update{it.copy(loading=false,error=e.message)}}}};fun clearError()=state.update{it.copy(error=null)};class Factory(private val c:AppContainer):ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST")override fun<T:ViewModel>create(modelClass:Class<T>)=ManagementReportViewModel(c.managementReportRepository)as T}}
private fun ReportPeriod.range():Pair<String,String>{val t=LocalDate.now();val f=when(this){ReportPeriod.Today->t;ReportPeriod.Last7->t.minusDays(6);ReportPeriod.Last30->t.minusDays(29);ReportPeriod.ThisMonth->t.withDayOfMonth(1)};return f.toString() to t.toString()}
