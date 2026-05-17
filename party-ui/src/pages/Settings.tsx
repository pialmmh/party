export default function Settings() {
    return (
        <>
            <div className="page-header">
                <div>
                    <h1>Settings</h1>
                    <div className="lede">
                        Read-only view of the operator / profile configuration loaded at
                        startup. Edit in <code>config/operators/btcl/dev/profile-dev.yml</code>.
                    </div>
                </div>
            </div>

            <div className="panel">
                <div className="panel-header">
                    <h2>Service</h2>
                </div>
                <div className="panel-body">
                    <div className="form-section">
                        <div className="form-row">
                            <label>Operator</label>
                            <input value="btcl" readOnly />
                        </div>
                        <div className="form-row">
                            <label>Profile</label>
                            <input value="dev" readOnly />
                        </div>
                        <div className="form-row">
                            <label>UI port</label>
                            <input value="7180" readOnly />
                        </div>
                        <div className="form-row">
                            <label>API base</label>
                            <input value="http://127.0.0.1:8081/api/v1" readOnly />
                        </div>
                    </div>
                </div>
            </div>

            <div className="panel">
                <div className="panel-header">
                    <h2>Keycloak</h2>
                </div>
                <div className="panel-body">
                    <div className="form-section">
                        <div className="form-row">
                            <label>URL</label>
                            <input value="http://127.0.0.1:8080" readOnly />
                        </div>
                        <div className="form-row">
                            <label>Realm</label>
                            <input value="btcl" readOnly />
                        </div>
                        <div className="form-row">
                            <label>SPI shared secret</label>
                            <input value="•••••• (loaded from secrets)" readOnly />
                        </div>
                    </div>
                </div>
            </div>

            <div className="panel">
                <div className="panel-header">
                    <h2>Default tenant</h2>
                </div>
                <div className="panel-body">
                    <div className="form-section">
                        <div className="form-row">
                            <label>Tenant ID</label>
                            <input value="t1" readOnly />
                        </div>
                        <div className="form-row">
                            <label>userRepoType</label>
                            <input value="odoo" readOnly />
                        </div>
                        <div className="form-row">
                            <label>Odoo URL</label>
                            <input value="http://10.10.199.41:7170" readOnly />
                        </div>
                        <div className="form-row">
                            <label>Odoo DB</label>
                            <input value="platform" readOnly />
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
}
