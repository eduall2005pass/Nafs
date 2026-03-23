package com.nafsshield.ui.report

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nafsshield.R
import com.nafsshield.data.model.BlockLog
import com.nafsshield.data.model.BlockReason
import com.nafsshield.data.repository.NafsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ReportFragment : Fragment() {

    private lateinit var repo: NafsRepository
    private lateinit var tvToday: TextView
    private lateinit var tvWeek: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvReasonApp: TextView
    private lateinit var tvReasonBrowser: TextView
    private lateinit var tvReasonKeyword: TextView
    private lateinit var tvReasonDns: TextView
    private lateinit var barContainer: LinearLayout
    private lateinit var barLabelContainer: LinearLayout
    private lateinit var recyclerLogs: RecyclerView
    private lateinit var tvNoLogs: TextView
    private lateinit var tvClearLogs: TextView
    private lateinit var logAdapter: LogAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_report, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        repo = NafsRepository.getInstance(requireContext())
        tvToday = v.findViewById(R.id.tvTodayNum)
        tvWeek  = v.findViewById(R.id.tvWeekNum)
        tvTotal = v.findViewById(R.id.tvTotalNum)
        tvReasonApp     = v.findViewById(R.id.tvReasonApp)
        tvReasonBrowser = v.findViewById(R.id.tvReasonBrowser)
        tvReasonKeyword = v.findViewById(R.id.tvReasonKeyword)
        tvReasonDns     = v.findViewById(R.id.tvReasonDns)
        barContainer      = v.findViewById(R.id.barChartContainer)
        barLabelContainer = v.findViewById(R.id.barLabelContainer)
        recyclerLogs = v.findViewById(R.id.recyclerLogs)
        tvNoLogs     = v.findViewById(R.id.tvNoLogs)
        tvClearLogs  = v.findViewById(R.id.tvClearLogs)

        logAdapter = LogAdapter(requireContext())
        recyclerLogs.layoutManager = LinearLayoutManager(requireContext())
        recyclerLogs.adapter = logAdapter

        tvClearLogs.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("লগ মুছবেন?")
                .setPositiveButton("Delete") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try { repo.cleanOldLogs() } catch (_: Exception) {}
                    }
                }
                .setNegativeButton("Cancel", null).show()
        }

        repo.recentLogs.observe(viewLifecycleOwner) { logs ->
            logAdapter.submitList(logs.take(30))
            tvNoLogs.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        }
        loadData()
    }

    override fun onResume() { super.onResume(); loadData() }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val logs = withContext(Dispatchers.IO) {
                try { repo.getWeeklyLogs() } catch (_: Exception) { emptyList() }
            }
            val now        = System.currentTimeMillis()
            val todayStart = now - (now % 86_400_000L)
            val weekStart  = now - 7L * 86_400_000L
            tvToday.text = logs.count { it.timestamp >= todayStart }.toString()
            tvWeek.text  = logs.count { it.timestamp >= weekStart }.toString()
            tvTotal.text = logs.size.toString()
            tvReasonApp.text     = logs.count { it.reason == BlockReason.APP_BLOCKED }.toString()
            tvReasonBrowser.text = logs.count { it.reason == BlockReason.BROWSER_NOT_ALLOWED }.toString()
            tvReasonKeyword.text = logs.count { it.reason == BlockReason.KEYWORD_FOUND }.toString()
            tvReasonDns.text     = logs.count { it.reason == BlockReason.DNS_BLOCKED }.toString()
            buildBarChart(logs)
        }
    }

    private fun buildBarChart(logs: List<BlockLog>) {
        barContainer.removeAllViews(); barLabelContainer.removeAllViews()
        val days  = listOf("রবি","সোম","মঙ্গ","বুধ","বৃহ","শুক্র","আজ")
        val sdf   = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val counts = (6 downTo 0).map { i ->
            val c = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, -i) }
            logs.count { sdf.format(Date(it.timestamp)) == sdf.format(c.time) } to
            days[6 - i]
        }
        val maxC = counts.maxOfOrNull { it.first }?.coerceAtLeast(1) ?: 1
        counts.forEachIndexed { i, (cnt, label) ->
            val isToday = i == 6
            val wrap = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                orientation  = LinearLayout.VERTICAL
                gravity      = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                setPadding(3,0,3,0)
            }
            if (cnt > 0) wrap.addView(TextView(requireContext()).apply {
                text = cnt.toString(); textSize = 9f
                setTextColor(Color.parseColor("#8B949E"))
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            wrap.addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    (100 * cnt / maxC).coerceAtLeast(if (cnt>0) 6 else 2))
                background = GradientDrawable().also {
                    it.shape = GradientDrawable.RECTANGLE; it.cornerRadius = 6f
                    it.setColor(if (isToday) Color.parseColor("#4F6EF5") else Color.parseColor("#21262D"))
                }
            })
            barContainer.addView(wrap)
            barLabelContainer.addView(TextView(requireContext()).apply {
                text = label; textSize = 9f
                setTextColor(if (isToday) Color.parseColor("#4F6EF5") else Color.parseColor("#8B949E"))
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
    }
}

class LogAdapter(private val ctx: Context) : RecyclerView.Adapter<LogAdapter.VH>() {
    private val items = mutableListOf<BlockLog>()
    private val tf = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val df = SimpleDateFormat("dd/MM", Locale.getDefault())
    private val todayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

    fun submitList(list: List<BlockLog>) { items.clear(); items.addAll(list); notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: TextView = v.findViewById(R.id.tvLogIcon)
        val app : TextView = v.findViewById(R.id.tvLogApp)
        val rsn : TextView = v.findViewById(R.id.tvLogReason)
        val time: TextView = v.findViewById(R.id.tvLogTime)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(ctx).inflate(R.layout.item_report_row, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val log = items[pos]
        h.app.text  = log.blockedPackage.split(".").lastOrNull() ?: log.blockedPackage
        h.icon.text = when(log.reason) {
            BlockReason.APP_BLOCKED -> "📱"; BlockReason.BROWSER_NOT_ALLOWED -> "🌐"
            BlockReason.KEYWORD_FOUND -> "🔑"; BlockReason.DNS_BLOCKED -> "🚫"
        }
        h.rsn.text = when(log.reason) {
            BlockReason.APP_BLOCKED -> "App ব্লক"
            BlockReason.BROWSER_NOT_ALLOWED -> "Browser ব্লক"
            BlockReason.KEYWORD_FOUND -> "Keyword: ${log.triggeredKeyword ?: ""}"
            BlockReason.DNS_BLOCKED -> "DNS ব্লক"
        }
        val d = Date(log.timestamp)
        h.time.text = if (SimpleDateFormat("yyyyMMdd",Locale.getDefault()).format(d) == todayKey)
            tf.format(d) else df.format(d)
    }
}
