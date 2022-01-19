package de.salomax.currencies.view.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModelProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import de.salomax.currencies.R
import de.salomax.currencies.util.getDecimalSeparator
import de.salomax.currencies.util.toHumanReadableNumber
import de.salomax.currencies.view.BaseActivity
import de.salomax.currencies.view.main.spinner.SearchableSpinner
import de.salomax.currencies.view.main.spinner.SearchableSpinnerAdapter
import de.salomax.currencies.view.preference.PreferenceActivity
import de.salomax.currencies.view.timeline.TimelineActivity
import de.salomax.currencies.viewmodel.main.CurrentInputViewModel
import de.salomax.currencies.viewmodel.main.ExchangeRatesViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle

class MainActivity : BaseActivity() {

    private lateinit var ratesModel: ExchangeRatesViewModel
    private lateinit var inputModel: CurrentInputViewModel

    private lateinit var refreshIndicator: LinearProgressIndicator
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var menuItemRefresh: MenuItem? = null

    private lateinit var tvCalculations: TextView
    private lateinit var tvFrom: TextView
    private lateinit var tvTo: TextView
    private lateinit var tvCurrencySymbolFrom: TextView
    private lateinit var tvCurrencySymbolTo: TextView
    private lateinit var spinnerFrom: SearchableSpinner
    private lateinit var spinnerTo: SearchableSpinner
    private lateinit var tvDate: TextView
    private lateinit var tvFee: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // general layout
        setContentView(R.layout.activity_main)
        title = null

        // apply localized numbers to the buttons
        findViewById<Button>(R.id.btn_decimal).text = getDecimalSeparator().toString()
        listOf(
            R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
            R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9
        ).forEach {
            val button = findViewById<Button>(it)
            button.text = String.format("%d", button.text.toString().toInt())
        }

        // model
        this.ratesModel = ViewModelProvider(this).get(ExchangeRatesViewModel::class.java)
        this.inputModel = ViewModelProvider(this).get(CurrentInputViewModel::class.java)

        // views
        this.refreshIndicator = findViewById(R.id.refreshIndicator)
        this.swipeRefresh = findViewById(R.id.swipeRefresh)
        this.tvCalculations = findViewById(R.id.textCalculations)
        this.tvFrom = findViewById(R.id.textFrom)
        this.tvTo = findViewById(R.id.textTo)
        this.tvCurrencySymbolFrom = findViewById(R.id.currencyFrom)
        this.tvCurrencySymbolTo = findViewById(R.id.currencyTo)
        this.spinnerFrom = findViewById(R.id.spinnerFrom)
        this.spinnerTo = findViewById(R.id.spinnerTo)
        this.tvDate = findViewById(R.id.textRefreshed)
        this.tvFee = findViewById(R.id.textFee)

        // swipe-to-refresh: color scheme (not accessible in xml)
        swipeRefresh.setColorSchemeResources(R.color.blackOlive)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.dollarBill)

        // listeners & stuff
        setListeners()

        // heavy lifting
        observe()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main, menu)
        this.menuItemRefresh = menu.findItem(R.id.refresh)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                startActivity(Intent(this, PreferenceActivity().javaClass))
                true
            }
            R.id.refresh -> {
                ratesModel.forceUpdateExchangeRate()
                true
            }
            R.id.timeline -> {
                val from = inputModel.getLastCurrencyFrom()
                val to = inputModel.getLastCurrencyTo()
                if (from != null && to != null) {
                    startActivity(
                        Intent(Intent(this, TimelineActivity().javaClass)).apply {
                            putExtra("ARG_FROM", from)
                            putExtra("ARG_TO", to)
                        }
                    )
                    true
                } else {
                    false
                }
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setListeners() {
        // long click on delete
        findViewById<ImageButton>(R.id.btn_delete).setOnLongClickListener {
            inputModel.clear()
            true
        }

        // long click on input "from"
        findViewById<LinearLayout>(R.id.clickFrom).setOnLongClickListener {
            val copyText = "${it.findViewById<TextView>(R.id.currencyFrom).text} ${it.findViewById<TextView>(R.id.textFrom).text}"
            copyToClipboard(copyText)
            true
        }
        // long click on input "to"
        findViewById<LinearLayout>(R.id.clickTo).setOnLongClickListener {
            val copyText = "${it.findViewById<TextView>(R.id.currencyTo).text} ${it.findViewById<TextView>(R.id.textTo).text}"
            copyToClipboard(copyText)
            true
        }

        // spinners: listen for changes
        spinnerFrom.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                inputModel.setRateFrom(
                    (parent?.adapter as SearchableSpinnerAdapter).getItem(position)
                )
            }
        }
        spinnerTo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                inputModel.setRateTo(
                    (parent?.adapter as SearchableSpinnerAdapter).getItem(position)
                )
            }
        }

        // swipe to refresh
        swipeRefresh.setOnRefreshListener {
            // update
            ratesModel.forceUpdateExchangeRate()
            swipeRefresh.isRefreshing = false
        }
    }

    private fun copyToClipboard(copyText: String) {
        // copy
        val clipboard: ClipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(null, copyText))
        // notify
        Snackbar.make(
            tvCalculations,
            HtmlCompat.fromHtml(
                getString(R.string.copied_to_clipboard, copyText),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            ),
            Snackbar.LENGTH_SHORT
        )
            .setBackgroundTint(getColor(R.color.colorAccent))
            .setTextColor(getColor(R.color.colorTextOnAccent))
            .show()
    }

    private fun observe() {
        //exchange rates changed
        ratesModel.exchangeRates.observe(this, {
            // date
            it?.let {
                val date = it.date
                val dateString = date?.format(
                    DateTimeFormatter
                        .ofLocalizedDate(FormatStyle.SHORT)
                        .withDecimalStyle(DecimalStyle.ofDefaultLocale())
                )

                tvDate.text = getString(R.string.last_updated, dateString)
                // today
                if (date?.isEqual(LocalDate.now()) == true)
                    tvDate.append(" (${getString(R.string.today)})")
                // yesterday
                else if (date?.isEqual(LocalDate.now().minusDays(1)) == true)
                    tvDate.append(" (${getString(R.string.yesterday)})")

                // paint text in red in case the data is old
                tvDate.setTextColor(
                    if (date?.isBefore(LocalDate.now().minusDays(3)) == true) getColor(android.R.color.holo_red_light)
                    else getTextColorSecondary()
                )
            }
            // rates
            spinnerFrom.setRates(it?.rates)
            spinnerTo.setRates(it?.rates)

            // restore state
            inputModel.getLastCurrencyFrom()?.let { last ->
                (spinnerFrom.adapter as? SearchableSpinnerAdapter)?.getPosition(last)?.let { position ->
                    spinnerFrom.setSelection(position)
                }
            }
            inputModel.getLastCurrencyTo()?.let { last ->
                (spinnerTo.adapter as? SearchableSpinnerAdapter)?.getPosition(last)?.let { position ->
                    spinnerTo.setSelection(position)
                }
            }
        })

        // stars changed
        ratesModel.getStarredCurrencies().observe(this, { stars ->
            // starred rates
            stars.let {
                spinnerFrom.setStars(it)
                spinnerTo.setStars(it)
            }

        })

        // something bad happened
        ratesModel.getError().observe(this, {
            // error
            it?.let {
                Snackbar.make(tvCalculations, it, 5000) // show for 5s
                    .setBackgroundTint(getColor(android.R.color.holo_red_light))
                    .setTextColor(getColor(android.R.color.white))
                    .show()
            }
        })

        // rates are updating
        ratesModel.isUpdating().observe(this, { isRefreshing ->
            refreshIndicator.visibility = if (isRefreshing) View.VISIBLE else View.GONE
            // disable manual refresh, while refreshing
            swipeRefresh.isEnabled = isRefreshing.not()
            menuItemRefresh?.isEnabled = isRefreshing.not()
        })

        // input changed
        inputModel.getCurrentBaseValueFormatted().observe(this, {
            tvFrom.text = it
        })
        inputModel.getResultFormatted().observe(this, {
            tvTo.text = it
        })
        inputModel.getCalculationInputFormatted().observe(this, {
            tvCalculations.text = it
        })
        inputModel.getBaseCurrency().observe(this, {
            tvCurrencySymbolFrom.text = it.symbol()
        })
        inputModel.getDestinationCurrency().observe(this, {
            tvCurrencySymbolTo.text = it.symbol()
        })

        // fee changed
        inputModel.isFeeEnabled().observe(this, {
            tvFee.visibility = if (it) View.VISIBLE else View.GONE
        })
        inputModel.getFee().observe(this, {
            tvFee.text = it.toHumanReadableNumber(showPositiveSign = true, suffix = "%")
            tvFee.setTextColor(
                if (it >= 0) getColor(android.R.color.holo_red_light)
                else getColor(R.color.dollarBill)
            )
        })
    }

    private fun getTextColorSecondary(): Int {
        val attrs = intArrayOf(android.R.attr.textColorSecondary)
        val a = theme.obtainStyledAttributes(R.style.AppTheme, attrs)
        val color = a.getColor(0, getColor(R.color.colorAccent))
        a.recycle()
        return color
    }

    /*
     * keyboard: number input
     */
    fun numberEvent(view: View) {
        inputModel.addNumber((view as Button).text.toString())
    }

    /*
     * keyboard: add decimal point
     */
    fun decimalEvent(@Suppress("UNUSED_PARAMETER") view: View) {
        inputModel.addDecimal()
    }

    /*
     * keyboard: delete
     */
    fun deleteEvent(@Suppress("UNUSED_PARAMETER") view: View) {
        inputModel.delete()
    }

    /*
     * keyboard: do some calculations
     */
    fun calculationEvent(view: View) {
        when((view as Button).text.toString()) {
            "+" -> inputModel.addition()
            "−" -> inputModel.subtraction()
            "×" -> inputModel.multiplication()
            "÷" -> inputModel.division()
        }
    }

    /*
     * swap currencies
     */
    fun toggleEvent(@Suppress("UNUSED_PARAMETER") view: View) {
        val from = spinnerFrom.selectedItemPosition
        val to = spinnerTo.selectedItemPosition
        spinnerFrom.setSelection(to)
        spinnerTo.setSelection(from)
    }

}
