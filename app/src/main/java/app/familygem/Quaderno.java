package app.familygem;

import static app.familygem.Global.gc;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;
import java.util.ArrayList;
import java.util.List;
import app.familygem.detail.Nota;
import app.familygem.visitor.ListaNote;
import app.familygem.visitor.TrovaPila;

public class Quaderno extends Fragment implements QuadernoAdapter.ItemClickListener {

	QuadernoAdapter adapter;

	public static List<Note> getAllNotes(boolean sharedOnly) {
		// Shared notes
		List<Note> sharedNotes = gc.getNotes();
		ArrayList<Note> noteList = new ArrayList<>();
		noteList.addAll(sharedNotes);
		// Inline notes
		if( !sharedOnly ) {
			ListaNote noteVisitor = new ListaNote();
			gc.accept(noteVisitor);
			noteList.addAll(noteVisitor.listaNote);
		}
		return noteList;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bandolo ) {
		View view = inflater.inflate(R.layout.ricicla_vista, container, false);
		RecyclerView recyclerView = view.findViewById(R.id.riciclatore);
		recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
		boolean sharedOnly = getActivity().getIntent().getBooleanExtra("quadernoScegliNota", false);
		List<Note> allNotes = getAllNotes(sharedOnly);
		adapter = new QuadernoAdapter(getContext(), allNotes, sharedOnly);
		adapter.setClickListener(this);
		recyclerView.setAdapter(adapter);

		((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(allNotes.size() + " "
				+ getString(allNotes.size() == 1 ? R.string.note : R.string.notes).toLowerCase());
		setHasOptionsMenu(allNotes.size() > 1);
		registerForContextMenu(recyclerView);
		view.findViewById(R.id.fab).setOnClickListener(v -> newNote(getContext(), null));
		return view;
	}

	// Andandosene dall'attività senza aver scelto una nota condivisa resetta l'extra
	@Override
	public void onPause() {
		super.onPause();
		getActivity().getIntent().removeExtra("quadernoScegliNota");
	}

	@Override
	public void onItemClick(View view, int position) {
		Note note = adapter.getItem(position);
		// Restituisce l'id di una nota a Individuo e Dettaglio
		if( getActivity().getIntent().getBooleanExtra("quadernoScegliNota", false) ) {
			Intent intento = new Intent();
			intento.putExtra("idNota", note.getId());
			getActivity().setResult(AppCompatActivity.RESULT_OK, intento);
			getActivity().finish();
		} else { // Apre il dettaglio della nota
			Intent intento = new Intent(getContext(), Nota.class);
			if( note.getId() != null ) { // Nota condivisa
				Memoria.setPrimo(note);
			} else { // Nota semplice
				new TrovaPila(gc, note);
				intento.putExtra("daQuaderno", true);
			}
			getContext().startActivity(intento);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if( item.getItemId() == 0 ) { // Elimina
			Object[] capi = U.eliminaNota(adapter.selectedNote, null);
			U.save(false, capi);
			getActivity().recreate();
		} else {
			return false;
		}
		return true;
	}

	// menu opzioni nella toolbar
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		// Search inside notes
		inflater.inflate(R.menu.cerca, menu);
		final SearchView searchView = (SearchView)menu.findItem(R.id.ricerca).getActionView();
		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextChange(String query) {
				adapter.getFilter().filter(query);
				return true;
			}
			@Override
			public boolean onQueryTextSubmit(String q) {
				searchView.clearFocus();
				return false;
			}
		});
	}

	// Crea una nuova nota condivisa, attaccata a un contenitore oppure slegata
	static void newNote(Context context, Object container) {
		Note note = new Note();
		String id = U.nuovoId(gc, Note.class);
		note.setId(id);
		note.setValue("");
		gc.addNote(note);
		if( container != null ) {
			NoteRef noteRef = new NoteRef();
			noteRef.setRef(id);
			((NoteContainer)container).addNoteRef(noteRef);
		}
		U.save(true, note);
		Memoria.setPrimo(note);
		context.startActivity(new Intent(context, Nota.class));
	}
}
