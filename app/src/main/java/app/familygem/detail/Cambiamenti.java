package app.familygem.detail;

import android.view.Menu;
import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.DateTime;
import app.familygem.Dettaglio;
import app.familygem.R;
import app.familygem.U;

public class Cambiamenti extends Dettaglio {

	Change c;

	@Override
	public void impagina() {
		setTitle(R.string.change_date);
		placeSlug("CHAN");
		c = (Change)cast(Change.class);
		DateTime dateTime = c.getDateTime();
		if( dateTime != null ) {
			if( dateTime.getValue() != null )
				U.metti(box, getString(R.string.value), dateTime.getValue());
			if( dateTime.getTime() != null )
				U.metti(box, getString(R.string.time), dateTime.getTime());
		}
		placeExtensions(c);
		U.placeNotes(box, c, true);
	}

	// Qui non c'è bisogno di un menu
	@Override
	public boolean onCreateOptionsMenu(Menu m) {
		return false;
	}
}
