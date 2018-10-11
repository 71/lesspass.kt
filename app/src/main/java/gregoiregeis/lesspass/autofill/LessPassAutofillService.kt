package gregoiregeis.lesspass.autofill

import android.os.CancellationSignal
import android.service.autofill.*

class LessPassAutofillService : AutofillService() {
    override fun onFillRequest(request: FillRequest?, cancellationSignal: CancellationSignal?, callback: FillCallback?) {
        val structure = request!!.fillContexts[request.fillContexts.size - 1].structure
        val packageName = structure.activityComponent.packageName
        val data = request.clientState

        callback!!.onFailure("Not implemented.")
    }

    override fun onSaveRequest(request: SaveRequest?, callback: SaveCallback?) {
        callback!!.onFailure("Not implemented.")
    }
}
