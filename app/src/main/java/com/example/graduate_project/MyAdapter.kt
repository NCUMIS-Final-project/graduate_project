

import android.icu.text.DecimalFormat
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.example.graduate_project.MapsActivity
import com.example.graduate_project.R

class MyAdapter() :
    RecyclerView.Adapter<MyAdapter.ViewHolder>() {
    /**
     *  把酒駕紀錄加進列表中的class
     */

    private var dataSet = mutableListOf<MapsActivity.Record>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val TextView1: TextView = itemView.findViewById(R.id.myTextView1)
        private val TextView2: TextView = itemView.findViewById(R.id.myTextView2)
        private var currentRecord: MapsActivity.Record? = null

        @RequiresApi(Build.VERSION_CODES.N)
        fun bind(record: MapsActivity.Record) {
            currentRecord = record
//            val date = SimpleDateFormat("yyyy/ M/ d").format(record.time)
//            TextView1.text = DecimalFormat("#.##").format(record.value)
            TextView1.text = String.format("%.2f",record.value)
            TextView2.text = SimpleDateFormat("yyyy/MM/dd").format(record.time)
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.item_myholder, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    @RequiresApi(Build.VERSION_CODES.N)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.bind(dataSet[position])
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size

    //更新資料用
    fun updateList(list: MutableList<MapsActivity.Record>){
        dataSet = list
    }
}
