import { useNavigate } from 'react-router'

function FacilityMenuPage() {
    const navigate = useNavigate()

    return (
        <div
            style={{
                backgroundColor: '#F3F5F1',
                minHeight: '100vh',
                padding: '50px 20px',
            }}
        >
            <div style={{ maxWidth: '900px', margin: '0 auto' }}>
                <h1
                    style={{
                        textAlign: 'center',
                        color: '#26332E',
                        fontSize: '28px',
                        fontWeight: 'bold',
                    }}
                >
                    공용시설 예약
                </h1>

                <p
                    style={{
                        textAlign: 'center',
                        color: '#64746B',
                        marginTop: '12px',
                        marginBottom: '35px',
                    }}
                >
                    이용하실 서비스를 선택해 주세요.
                </p>

                <div
                    style={{
                        display: 'grid',
                        gridTemplateColumns:
                            'repeat(auto-fit, minmax(250px, 1fr))',
                        gap: '20px',
                    }}
                >
                    <div style={cardStyle}>
                        <h2 style={titleStyle}>공용시설 예약</h2>

                        <p style={descriptionStyle}>
                            공용시설을 조회하고 예약할 수 있습니다.
                        </p>

                        <button
                            style={buttonStyle}
                            onClick={() => navigate('/facilities/resources')}
                        >
                            예약하기
                        </button>
                    </div>

                    <div style={cardStyle}>
                        <h2 style={titleStyle}>공용시설 예약 내역</h2>

                        <p style={descriptionStyle}>
                            나의 예약 내역을 확인하고 관리할 수 있습니다.
                        </p>

                        <button
                            style={buttonStyle}
                            onClick={() => navigate('/facilities/reservations')}
                        >
                            내역 보기
                        </button>
                    </div>
                </div>
            </div>
        </div>
    )
}

const cardStyle = {
    backgroundColor: '#FFFFFF',
    padding: '35px 25px',
    borderRadius: '12px',
    textAlign: 'center',
    border: '1px solid #E0E8E2',
}

const titleStyle = {
    color: '#26332E',
    fontSize: '20px',
    fontWeight: 'bold',
    marginBottom: '15px',
}

const descriptionStyle = {
    color: '#64746B',
    fontSize: '14px',
    marginBottom: '25px',
}

const buttonStyle = {
    backgroundColor: '#52796F',
    color: '#FFFFFF',
    padding: '12px 25px',
    border: 'none',
    borderRadius: '8px',
    cursor: 'pointer',
    width: '100%',
}

export default FacilityMenuPage