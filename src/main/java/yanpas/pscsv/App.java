package yanpas.pscsv;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;

class Retriever {
	private String out_dir;
	private Connection con;
	private final String uri = "jdbc:postgresql://";
	private ArrayList<String> tables = new ArrayList<>();

	public Retriever(String user, String password, String db_name, String address)
			throws SQLException {
		con = DriverManager.getConnection(uri + address + "/" + db_name, user, password);
	}

	private void appendTables() {
		try {
			DatabaseMetaData dbmd = con.getMetaData();
			ResultSet tabs = dbmd.getTables(null, null, "%", new String[] { "TABLE" });
			while (tabs.next())
				tables.add(tabs.getString("TABLE_NAME"));

		} catch (SQLException sqlEx) {
			sqlEx.printStackTrace();
			try {
				con.close();
			} catch (SQLException e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	private void exportTable(String table_name) throws SQLException, IOException, Exception {
		ResultSet rs = null;
		Statement st;
		ArrayList<String> columns = new ArrayList<>();

		try (FileWriter fout = new FileWriter(out_dir + "/" + table_name + ".csv")) {
			st = con.createStatement();
			rs = st.executeQuery("SELECT * FROM " + table_name);
			ResultSetMetaData rsmd = rs.getMetaData();
			int nColumns = rsmd.getColumnCount();

			for (int i = 1; i <= nColumns; ++i) {
				columns.add(rsmd.getColumnName(i));
				String colName = columns.get(i - 1);
				fout.write('"'+(colName==null ? "UNNAMED": colName)+'"');
				if (i < nColumns)
					fout.write(',');
			}
			fout.write('\n');

			while (rs.next()) {
				for (String col : columns) {
					String r = rs.getString(col);
					if (r != null) {
						fout.write('"'+r+'"');
					}
					fout.write(',');
				}
				fout.write('\n');
			}
		}
	}

	public void exportAll(String outputdir) throws SQLException, IOException, Exception {
		this.out_dir = outputdir;
		appendTables();
		for (String t : tables)
			exportTable(t);
	}

}

@SuppressWarnings("serial")
class Frame extends JFrame {
	private JLabel username = new JLabel("User name:"),
			password = new JLabel("Password:"),
			db_name = new JLabel("Database name:"),
			addr = new JLabel("Address:Port");
	private JTextField username_t = new JTextField(),
			password_t = new JTextField(),
			db_name_t = new JTextField(),
			addr_t = new JTextField();
	private JPanel pan = new JPanel();
	private JButton but = new JButton("Gather all tables!");
	private ActionListener alistener = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent e) {

			JFileChooser fd = new JFileChooser();
			fd.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			fd.showSaveDialog(Frame.this);
			String outputdir = fd.getSelectedFile().getAbsolutePath();
			if (outputdir == null)
				return;
			if (!new File(outputdir).exists() || !new File(outputdir).isDirectory()) {
				JOptionPane.showMessageDialog(Frame.this,
						"Out path: " + outputdir + " is wrong", "Error",
						JOptionPane.ERROR_MESSAGE);
				return;
			}

			try {
				Retriever r =
						new Retriever(username_t.getText(), password_t.getText(),
								db_name_t.getText(), addr_t.getText());
				r.exportAll(outputdir);
			} catch (SQLException e1) {
				e1.printStackTrace();
				JOptionPane.showMessageDialog(Frame.this,
						"SQL exception caught:\n" + e1.getMessage()
								+ "\nStack trace was written to console",
						"DB Error", JOptionPane.ERROR_MESSAGE);
				return;
			} catch (IOException e1) {
				e1.printStackTrace();
				JOptionPane.showMessageDialog(Frame.this,
						"Export exception caught:\n" + e1.getMessage()
								+ "\nStack trace was written to console",
						"I/O Error", JOptionPane.ERROR_MESSAGE);
				return;
			} catch (Exception e1) {
				JOptionPane.showMessageDialog(Frame.this,
						"Exception caught:\n" + e1.getMessage(), "Some other kind of error",
						JOptionPane.ERROR_MESSAGE);
				return;
			}
			JOptionPane.showMessageDialog(Frame.this, "Completed successfully", "Completed",
					JOptionPane.INFORMATION_MESSAGE);
		}
	};

	public Frame() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Throwable e) {
			e.printStackTrace();
		}
		setTitle("PostgreSQL csv gatherer");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		packAll();
		setMinimumSize(new Dimension(400, 0));
	}

	private void packAll() {
		this.getContentPane().setLayout(new BorderLayout(5, 5));
		pan.setLayout(new GridLayout(0, 2));
		pan.add(username);
		pan.add(username_t);
		pan.add(password);
		pan.add(password_t);
		pan.add(db_name);
		pan.add(db_name_t);
		pan.add(addr);
		pan.add(addr_t);
		pan.setBorder(BorderFactory.createBevelBorder(5));
		add(pan);
		add(but, BorderLayout.SOUTH);

		but.addActionListener(alistener);
		pack();
	}

}

public class App {
	public static void main(String[] args) {
		if (args.length > 0 && args[0].equals("-cli")) {
			try {
				Retriever r = new Retriever(args[1], args[2], args[3], args[4]);
				r.exportAll("csvs");
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			System.out.println("CLI usage: java -jar app.jar -cli username password db_name ip:port exportdir");
			Frame f = new Frame();
			f.setVisible(true);
		}
	}
}
